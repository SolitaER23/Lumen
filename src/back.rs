//! 预测性返回（Android 13+ / `OnBackInvokedCallback`）。
//!
//! 系统在手势进行中会持续回调 `onBackStarted / onBackProgressed`，把 0..1 的进度
//! 交给我们。UI 拿这个进度**直接驱动页面位移**（1:1 跟手），松手后由弹簧接着走完
//! 或者弹回——这样"跟手、展开、返回"是同一段连续运动，而不是松手后才开始动画。
//!
//! Java 侧只有一个 40 行的桥（`BackBridge.java`，平台回调没法在纯 Rust 里实现），
//! 它把 4 个回调转成这里的 4 个 native 方法；页面逻辑仍然全在 Rust。

use std::sync::atomic::{AtomicU32, AtomicU8, Ordering};

/// 无手势
pub const IDLE: u8 = 0;
/// 手势进行中（进度有效）
pub const ACTIVE: u8 = 1;
/// 系统判定返回（松手提交）
pub const INVOKED: u8 = 2;
/// 手势取消（弹回）
pub const CANCELLED: u8 = 3;

static STATE: AtomicU8 = AtomicU8::new(IDLE);
static BRIDGE: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
static PROGRESS: AtomicU32 = AtomicU32::new(0);

#[allow(dead_code)]
fn set_progress(p: f32) {
    PROGRESS.store(p.to_bits(), Ordering::Relaxed);
    STATE.store(ACTIVE, Ordering::Release);
}

/// 系统桥是否装上（装上了就不要再吃 KEYCODE_BACK，否则会双重返回）
pub fn has_bridge() -> bool {
    BRIDGE.load(Ordering::Relaxed)
}

pub fn mark_bridge() {
    BRIDGE.store(true, Ordering::Relaxed);
}

/// 当前状态（不消费）。`INVOKED` / `CANCELLED` 由调用方处理完后 [`clear`]。
pub fn state() -> u8 {
    STATE.load(Ordering::Acquire)
}

/// 0..1 的手势进度
pub fn progress() -> f32 {
    f32::from_bits(PROGRESS.load(Ordering::Relaxed)).clamp(0.0, 1.0)
}

/// 处理完一次性事件后复位
pub fn clear() {
    STATE.store(IDLE, Ordering::Release);
    PROGRESS.store(0f32.to_bits(), Ordering::Relaxed);
}

// ---------------------------------------------------------------------------
// Java 桥（只在 Android 上编）
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
mod imp {
    use super::*;
    use jni::objects::JClass;
    use jni::sys::jfloat;
    use jni::JNIEnv;

    #[no_mangle]
    pub extern "system" fn Java_com_example_app_BackBridge_nativeStarted(
        _env: JNIEnv,
        _class: JClass,
        progress: jfloat,
    ) {
        log::info!("back: started p={progress:.2}");
        set_progress(progress);
    }

    #[no_mangle]
    pub extern "system" fn Java_com_example_app_BackBridge_nativeProgress(
        _env: JNIEnv,
        _class: JClass,
        progress: jfloat,
    ) {
        // 进度回调很密：只按 0.1 的档记一条，别刷爆 logcat
        let bucket = (progress * 10.0) as u32;
        static LOGGED: AtomicU32 = AtomicU32::new(u32::MAX);
        if LOGGED.swap(bucket, Ordering::Relaxed) != bucket {
            log::info!("back: progress {progress:.2}");
        }
        set_progress(progress);
    }

    #[no_mangle]
    pub extern "system" fn Java_com_example_app_BackBridge_nativeCancelled(_env: JNIEnv, _class: JClass) {
        log::info!("back: cancelled");
        STATE.store(CANCELLED, Ordering::Release);
    }

    #[no_mangle]
    pub extern "system" fn Java_com_example_app_BackBridge_nativeInvoked(_env: JNIEnv, _class: JClass) {
        log::info!("back: invoked");
        STATE.store(INVOKED, Ordering::Release);
    }
}
