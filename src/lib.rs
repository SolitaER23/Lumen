//! Lumen —— 本地音乐播放器（Rust + egui，Windows Phone Metro 风格）。
//!
//! Rust 编写的 Android 应用：UI 全部自绘，通过 `GameActivity` 加载 `libapp.so`
//! （用 GameActivity 而非 NativeActivity，是为了获得完整的 IME/软键盘支持）。
//! 桌面端用同一份代码调试。

pub mod anim;
pub mod audio;
pub mod back;
pub mod eventlog;
pub mod cache;
pub mod app;
pub mod geometry;
pub mod ime;
pub mod m3;
pub mod metro;
pub mod model;
pub mod range_source;
pub mod remote;
pub mod safe_area;
pub mod settings;
pub mod subsonic;
pub mod theme;

pub use app::App;

// ---------------------------------------------------------------------------
// Android 入口：由 android-activity 的 GameActivity glue 调用
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn android_main(app: winit::platform::android::activity::AndroidApp) {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );
    // 一个进程只应有一个 eframe 循环。Activity 被系统重建时 android_main 会再进来一次，
    // 若上一个循环还活着，两个 GL 循环会抢 surface（表现就是卡在启动封面、音乐还在放）。
    // 这里至少留一条可诊断的日志，方便从 logcat 判断是不是"重入"。
    {
        use std::sync::atomic::{AtomicU32, Ordering};
        static ENTRIES: AtomicU32 = AtomicU32::new(0);
        let n = ENTRIES.fetch_add(1, Ordering::Relaxed) + 1;
        if n > 1 {
            log::warn!("android_main re-entered (entry #{n}) — 关闭上一轮事件循环");
            crate::app::close_previous_loop();
        }
    }
    log::info!("android_main entered");

    // 先问系统要安全区（刘海/挖孔），再进入事件循环
    let (safe_top, safe_bottom) = safe_area::query(&app);
    safe_area::store_handles(app.vm_as_ptr(), app.activity_as_ptr());
    safe_area::store_app(app.clone());
    // 预测性返回：把系统手势进度接进 UI（失败就退回 KEYCODE_BACK）
    safe_area::install_back_bridge();
    // 120Hz 的请求放到首帧之后（那时 surface 才建好）

    // 打开 vsync：交换缓冲会被屏幕的刷新节拍挡住，帧间隔天然是 16.7ms（60Hz）
    // 或 8.3ms（120Hz），不会出现"自己按 122fps 送帧、屏幕按 60Hz 取帧"的错拍。
    // 之前关 vsync 是因为旧机器上 eglSwapInterval(1) 会锁死 60Hz；
    // 现在交给 vsync 决定节拍，既不丢帧也不空转。
    let options = eframe::NativeOptions {
        android_app: Some(app),
        ..Default::default()
    };

    if let Err(err) = eframe::run_native(
        "Lumen",
        options,
        Box::new(move |cc| {
            let mut a = App::new(cc);
            a.set_safe_area(safe_top, safe_bottom);
            Ok(Box::new(a))
        }),
    ) {
        log::error!("eframe failed: {err}");
    }
}
