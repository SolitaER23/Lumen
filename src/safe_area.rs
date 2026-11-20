//! 读取 Android 的安全区（刘海 / 挖孔 / 状态栏高度），让界面避开前置摄像头。
//!
//! 纯 Rust 拿不到 `WindowInsets`，所以这里用 JNI 调 `Activity`：
//! `getWindow().getDecorView().getRootWindowInsets().getDisplayCutout().getSafeInsetTop()`
//! 拿不到（老系统 / 无挖孔）就退回 `status_bar_height` 资源值。任何一步失败都只影响内边距，
//! 不会影响 App 启动。

#[cfg(target_os = "android")]
pub fn query(app: &winit::platform::android::activity::AndroidApp) -> (f32, f32) {
    let (top, bottom) = unsafe {
        imp::query(app.vm_as_ptr(), app.activity_as_ptr())
    };
    log::info!("safe area (px): top={top} bottom={bottom}");
    (top, bottom)
}

#[cfg(not(target_os = "android"))]
pub fn query_default() -> (f32, f32) {
    (0.0, 0.0)
}

#[cfg(target_os = "android")]
mod imp {
    use jni::objects::{JObject, JValue};
    use jni::sys::{jobject, JavaVM as RawVm};
    use jni::{JNIEnv, JavaVM};
    use std::ffi::c_void;

    pub unsafe fn query(vm_ptr: *mut c_void, activity_ptr: *mut c_void) -> (f32, f32) {
        let Ok(vm) = JavaVM::from_raw(vm_ptr as *mut RawVm) else {
            return (0.0, 0.0);
        };
        let Ok(mut env) = vm.attach_current_thread() else {
            return (0.0, 0.0);
        };
        let activity = JObject::from_raw(activity_ptr as jobject);
        read(&mut env, &activity)
    }

    fn read(env: &mut JNIEnv, activity: &JObject) -> (f32, f32) {
        let mut top = 0i32;
        let mut bottom = 0i32;

        // 有挖孔就用挖孔的安全区，没有就退回状态栏高度
        if let Some((t, b)) = cutout(env, activity) {
            top = top.max(t);
            bottom = bottom.max(b);
        }
        // 系统栏（状态栏 + 导航栏）：三键导航时底部会多出 ~63px，
        // 只看挖孔是拿不到的。API 30+ 才有 WindowInsets.getInsets。
        if let Some((t, b)) = system_bars(env, activity) {
            top = top.max(t);
            bottom = bottom.max(b);
        }
        if top == 0 {
            if let Some(px) = dimen(env, activity, "status_bar_height") {
                top = px;
            }
        }
        (top as f32, bottom as f32)
    }

    /// `WindowInsets.getInsets(Type.systemBars())` 的 (top, bottom)。
    fn system_bars(env: &mut JNIEnv, activity: &JObject) -> Option<(i32, i32)> {
        let window = env
            .call_method(activity, "getWindow", "()Landroid/view/Window;", &[])
            .ok()?
            .l()
            .ok()?;
        let decor = env
            .call_method(&window, "getDecorView", "()Landroid/view/View;", &[])
            .ok()?
            .l()
            .ok()?;
        let insets = env
            .call_method(
                &decor,
                "getRootWindowInsets",
                "()Landroid/view/WindowInsets;",
                &[],
            )
            .ok()?
            .l()
            .ok()?;
        if insets.is_null() {
            return None;
        }
        // WindowInsets.Type.systemBars() —— 静态方法，返回 int mask
        let cls = env.find_class("android/view/WindowInsets$Type").ok()?;
        let mask = env
            .call_static_method(cls, "systemBars", "()I", &[])
            .ok()?
            .i()
            .ok()?;
        let i = env
            .call_method(
                &insets,
                "getInsets",
                "(I)Landroid/graphics/Insets;",
                &[JValue::Int(mask)],
            )
            .ok()?
            .l()
            .ok()?;
        if i.is_null() {
            return None;
        }
        let top = env.get_field(&i, "top", "I").ok()?.i().ok()?;
        let bottom = env.get_field(&i, "bottom", "I").ok()?.i().ok()?;
        Some((top, bottom))
    }

    /// 取 android 系统资源里的尺寸（dp -> px 已由系统换算）
    fn dimen(env: &mut JNIEnv, activity: &JObject, name: &str) -> Option<i32> {
        let res = env
            .call_method(activity, "getResources", "()Landroid/content/res/Resources;", &[])
            .ok()?
            .l()
            .ok()?;
        let n = env.new_string(name).ok()?;
        let d = env.new_string("dimen").ok()?;
        let p = env.new_string("android").ok()?;
        let id = env
            .call_method(
                &res,
                "getIdentifier",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",
                &[JValue::Object(&n), JValue::Object(&d), JValue::Object(&p)],
            )
            .ok()?
            .i()
            .ok()?;
        if id == 0 {
            return None;
        }
        env.call_method(&res, "getDimensionPixelSize", "(I)I", &[JValue::Int(id)])
            .ok()?
            .i()
            .ok()
    }

    /// 挖孔/刘海安全区，API 28 以下没有这个方法
    fn cutout(env: &mut JNIEnv, activity: &JObject) -> Option<(i32, i32)> {
        let window = env
            .call_method(activity, "getWindow", "()Landroid/view/Window;", &[])
            .ok()?
            .l()
            .ok()?;
        let decor = env
            .call_method(&window, "getDecorView", "()Landroid/view/View;", &[])
            .ok()?
            .l()
            .ok()?;
        let insets = match env.call_method(
            &decor,
            "getRootWindowInsets",
            "()Landroid/view/WindowInsets;",
            &[],
        ) {
            Ok(v) => v.l().ok()?,
            Err(_) => {
                let _ = env.exception_clear();
                return None;
            }
        };
        if insets.is_null() {
            return None;
        }
        let cut = match env.call_method(
            &insets,
            "getDisplayCutout",
            "()Landroid/graphics/DisplayCutout;",
            &[],
        ) {
            Ok(v) => v.l().ok()?,
            Err(_) => {
                let _ = env.exception_clear();
                return None;
            }
        };
        if cut.is_null() {
            return None;
        }
        let t = env
            .call_method(&cut, "getSafeInsetTop", "()I", &[])
            .ok()?
            .i()
            .ok()?;
        let b = env
            .call_method(&cut, "getSafeInsetBottom", "()I", &[])
            .ok()?
            .i()
            .ok()?;
        Some((t, b))
    }
}

// ---------------------------------------------------------------------------
// 系统返回：根视图时结束 Activity
// ---------------------------------------------------------------------------

use std::sync::OnceLock;

static HANDLES: OnceLock<(usize, usize)> = OnceLock::new();

/// 保留一份 AndroidApp：需要窗口指针（`native_window()`）时随时取
#[cfg(target_os = "android")]
static APP: OnceLock<winit::platform::android::activity::AndroidApp> = OnceLock::new();

/// 记下 AndroidApp（可克隆，内部是 Arc）
#[cfg(target_os = "android")]
pub fn store_app(app: winit::platform::android::activity::AndroidApp) {
    let _ = APP.set(app);
}

/// IME 编辑会话快照：`(text, composingRegion, selection)`（下标都是**字符**）。
///
/// 真实输入法（软键盘）的文字只能这样拿到：winit 0.30 的 Android 后端不转发
/// IME 事件（commitText / setComposingText），只处理 KeyEvent——所以 ADB 注入
/// 能输、真实键盘不能。GameActivity 的 GameTextInput 是唯一的状态来源。
#[cfg(target_os = "android")]
pub fn ime_state() -> Option<(String, Option<(usize, usize)>, (usize, usize))> {
    let app = APP.get()?;
    let s = app.text_input_state();
    let compose = s.compose_region.map(|r| (r.start, r.end));
    Some((s.text, compose, (s.selection.start, s.selection.end)))
}
#[cfg(not(target_os = "android"))]
pub fn ime_state() -> Option<(String, Option<(usize, usize)>, (usize, usize))> {
    None
}

/// 输入框拿到焦点：声明编辑器类型（密码框用 PASSWORD 变体，输入法就不会联想/记忆），
/// 把文本和光标交给 IME，并主动把软键盘叫出来。
///
/// 不声明编辑器类型的话 `text_input_state` 一直是空的，真实键盘一个字都进不来。
#[cfg(target_os = "android")]
pub fn ime_begin(text: &str, cursor: usize, password: bool) {
    use winit::platform::android::activity::input::{
        ImeOptions, InputType, TextInputAction, TextInputState, TextSpan,
    };
    let Some(app) = APP.get() else {
        return;
    };
    let kind = if password {
        InputType::TYPE_CLASS_TEXT | InputType::TYPE_TEXT_VARIATION_PASSWORD
    } else {
        InputType::TYPE_CLASS_TEXT
    };
    app.set_ime_editor_info(kind, TextInputAction::Unspecified, ImeOptions::empty());
    app.set_text_input_state(TextInputState {
        text: text.to_owned(),
        selection: TextSpan {
            start: cursor,
            end: cursor,
        },
        compose_region: None,
    });
    app.show_soft_input(true);
}
#[cfg(not(target_os = "android"))]
pub fn ime_begin(_text: &str, _cursor: usize, _password: bool) {}

/// 把当前文本/光标同步给 IME（用户用硬件键盘或程序化改过缓冲区时）。
#[cfg(target_os = "android")]
pub fn ime_set(text: &str, cursor: usize) {
    use winit::platform::android::activity::input::{TextInputState, TextSpan};
    let Some(app) = APP.get() else {
        return;
    };
    app.set_text_input_state(TextInputState {
        text: text.to_owned(),
        selection: TextSpan {
            start: cursor,
            end: cursor,
        },
        compose_region: None,
    });
}
#[cfg(not(target_os = "android"))]
pub fn ime_set(_text: &str, _cursor: usize) {}

/// 结束编辑会话（失焦 / 退出登录）：清掉 IME 侧缓存的文本，避免下次输入拼到旧内容上。
#[cfg(target_os = "android")]
pub fn ime_end() {
    use winit::platform::android::activity::input::{TextInputState, TextSpan};
    if let Some(app) = APP.get() {
        app.set_text_input_state(TextInputState {
            text: String::new(),
            selection: TextSpan { start: 0, end: 0 },
            compose_region: None,
        });
    }
}
#[cfg(not(target_os = "android"))]
pub fn ime_end() {}

/// 系统动画倍率（`Settings.Global.animator_duration_scale`）。
///
/// 0 = 关闭动画（无障碍里的「移除动画」，开发者选项的动画缩放也会改它），
/// 1 = 正常，0.5/1.5/2 = 系统的动画速度设置。读不到返回 None（按 1 处理）。
#[cfg(target_os = "android")]
pub fn animator_scale() -> Option<f32> {
    use jni::objects::{JObject, JValue};
    use jni::sys::{jobject, JavaVM as RawVm};
    use jni::JavaVM;

    let (vm, act) = HANDLES.get().copied()?;
    unsafe {
        let vm = JavaVM::from_raw(vm as *mut RawVm).ok()?;
        let mut env = vm.attach_current_thread().ok()?;
        let activity = JObject::from_raw(act as jobject);
        let resolver = env
            .call_method(
                &activity,
                "getContentResolver",
                "()Landroid/content/ContentResolver;",
                &[],
            )
            .ok()?
            .l()
            .ok()?;
        let class = env
            .find_class("android/provider/Settings$Global")
            .ok()?;
        let key = env.new_string("animator_duration_scale").ok()?;
        let v = env
            .call_static_method(
                &class,
                "getFloat",
                "(Landroid/content/ContentResolver;Ljava/lang/String;F)F",
                &[
                    JValue::Object(&resolver),
                    JValue::Object(&key),
                    JValue::Float(1.0),
                ],
            )
            .ok()?
            .f()
            .ok()?;
        Some(v.clamp(0.0, 2.0))
    }
}
#[cfg(not(target_os = "android"))]
pub fn animator_scale() -> Option<f32> {
    None
}

/// 记下 JavaVM 和 Activity 的裸指针，供返回键退出使用（非 Android 平台是空操作）
pub fn store_handles(vm: *mut std::ffi::c_void, activity: *mut std::ffi::c_void) {
    let _ = HANDLES.set((vm as usize, activity as usize));
}

/// 重新读一次安全区（系统栏 / 手势条切换后布局要跟着变）。
///
/// 用启动时存下的句柄，任意线程可调；读失败返回 `None`，调用方保持原值。
#[cfg(target_os = "android")]
pub fn current_insets() -> Option<(f32, f32)> {
    let (vm, act) = HANDLES.get().copied()?;
    let (top, bottom) = unsafe {
        imp::query(vm as *mut std::ffi::c_void, act as *mut std::ffi::c_void)
    };
    // 全 0 视为读取失败（正常设备顶部至少有状态栏）
    if top <= 0.0 && bottom <= 0.0 {
        None
    } else {
        Some((top, bottom))
    }
}

#[cfg(not(target_os = "android"))]
pub fn current_insets() -> Option<(f32, f32)> {
    None
}

/// 当前 native window（surface）的实际像素尺寸。旋转后如果这个尺寸变了而 egui 的
/// screen_rect 没变，就说明 resize 事件没送到 eframe（诊断横竖屏切换用）。
#[cfg(target_os = "android")]
pub fn native_window_size() -> Option<(u32, u32)> {
    let app = APP.get()?;
    let win = app.native_window()?;
    Some((win.width() as u32, win.height() as u32))
}

#[cfg(not(target_os = "android"))]
pub fn native_window_size() -> Option<(u32, u32)> {
    None
}

/// 调 `activity.finish()`，让系统返回动画正常播放（不要直接 exit 进程）
#[cfg(target_os = "android")]
pub fn finish_activity() {
    use jni::objects::JObject;
    use jni::sys::{jobject, JavaVM as RawVm};
    use jni::JavaVM;

    let Some((vm, act)) = HANDLES.get().copied() else {
        return;
    };
    unsafe {
        let Ok(vm) = JavaVM::from_raw(vm as *mut RawVm) else {
            return;
        };
        let Ok(mut env) = vm.attach_current_thread() else {
            return;
        };
        let activity = JObject::from_raw(act as jobject);
        if env
            .call_method(&activity, "finish", "()V", &[])
            .is_err()
        {
            let _ = env.exception_clear();
        }
    }
}

/// 装上预测性返回桥（`BackBridge.install(activity)`）。
///
/// 注册 `OnBackInvokedCallback` 之后，系统在手势进行中就会把 0..1 的进度
/// 回调给 [`crate::back`]，UI 直接拿它驱动页面位移（跟手）。
/// 装不上（API < 33 / 类缺失）时保持 KEYCODE_BACK 的老路径。
#[cfg(target_os = "android")]
pub fn install_back_bridge() {
    use jni::objects::{JObject, JValue};
    use jni::sys::{jobject, JavaVM as RawVm};
    use jni::JavaVM;

    let Some((vm, act)) = HANDLES.get().copied() else {
        log::warn!("back: activity handles missing, predictive back off");
        return;
    };
    unsafe {
        let Ok(vm) = JavaVM::from_raw(vm as *mut RawVm) else {
            return;
        };
        let Ok(mut env) = vm.attach_current_thread() else {
            return;
        };
        let activity = JObject::from_raw(act as jobject);
        // 从 native 线程 `find_class` 走的是系统类加载器，看不到 APK 里的类；
        // 用 Activity 自己的 ClassLoader 去载入，才能拿到 BackBridge。
        let Ok(loader) = env
            .call_method(&activity, "getClassLoader", "()Ljava/lang/ClassLoader;", &[])
            .and_then(|v| v.l())
        else {
            let _ = env.exception_clear();
            log::warn!("back: class loader unavailable");
            return;
        };
        let Ok(name) = env.new_string("com.example.app.BackBridge") else {
            log::warn!("back: new_string failed");
            return;
        };
        let Ok(class) = env
            .call_method(
                &loader,
                "loadClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                &[JValue::Object(&name)],
            )
            .and_then(|v| v.l())
        else {
            let _ = env.exception_clear();
            log::warn!("back: BackBridge not loadable (predictive back off)");
            return;
        };
        let class = unsafe { jni::objects::JClass::from_raw(class.into_raw()) };
        match env.call_static_method(
            &class,
            "install",
            "(Landroid/app/Activity;)V",
            &[JValue::Object(&activity)],
        ) {
            Ok(_) => {
                crate::back::mark_bridge();
                log::info!("back: predictive back bridge installed");
            }
            Err(e) => {
                let _ = env.exception_clear();
                log::warn!("back: bridge install failed: {e:?}");
            }
        }
    }
}

#[cfg(not(target_os = "android"))]
pub fn install_back_bridge() {}

#[cfg(not(target_os = "android"))]
pub fn finish_activity() {}
/// 应用缓存目录（`context.getCacheDir()`），音频和封面都缓存在这里。
/// 拿不到就返回 None，调用方退回临时目录。
#[cfg(target_os = "android")]
pub fn cache_dir() -> Option<String> {
    use jni::objects::JObject;
    use jni::sys::{jobject, JavaVM as RawVm};
    use jni::JavaVM;

    let (vm, act) = HANDLES.get().copied()?;
    unsafe {
        let vm = JavaVM::from_raw(vm as *mut RawVm).ok()?;
        let mut env = vm.attach_current_thread().ok()?;
        let activity = JObject::from_raw(act as jobject);
        let file = env
            .call_method(&activity, "getCacheDir", "()Ljava/io/File;", &[])
            .ok()?
            .l()
            .ok()?;
        let path = env
            .call_method(&file, "getAbsolutePath", "()Ljava/lang/String;", &[])
            .ok()?
            .l()
            .ok()?;
        let s: String = env.get_string(&path.into()).ok()?.into();
        Some(s)
    }
}

#[cfg(not(target_os = "android"))]
pub fn cache_dir() -> Option<String> {
    None
}

/// 应用私有目录（`context.getFilesDir()`），设置文件写在这里。
#[cfg(target_os = "android")]
pub fn files_dir() -> Option<String> {
    dir_from_activity("getFilesDir")
}

#[cfg(target_os = "android")]
fn dir_from_activity(method: &str) -> Option<String> {
    use jni::objects::JObject;
    use jni::sys::{jobject, JavaVM as RawVm};
    use jni::JavaVM;

    let (vm, act) = HANDLES.get().copied()?;
    unsafe {
        let vm = JavaVM::from_raw(vm as *mut RawVm).ok()?;
        let mut env = vm.attach_current_thread().ok()?;
        let activity = JObject::from_raw(act as jobject);
        let file = env
            .call_method(&activity, method, "()Ljava/io/File;", &[])
            .ok()?
            .l()
            .ok()?;
        let path = env
            .call_method(&file, "getAbsolutePath", "()Ljava/lang/String;", &[])
            .ok()?
            .l()
            .ok()?;
        let s: String = env.get_string(&path.into()).ok()?.into();
        Some(s)
    }
}

#[cfg(not(target_os = "android"))]
pub fn files_dir() -> Option<String> {
    None
}

/// 屏幕底部圆角半径（px）。`RoundedCorner.POSITION_BOTTOM_LEFT`（API 31+）。
///
/// 底部常驻的迷你播放条贴着屏幕下缘，左右两端会落进圆角的裁切区，
/// 所以需要知道半径来给内容留安全距离。拿不到（API < 31 / 无显示）返回 0，
/// 调用方按"没有圆角"处理即可。
#[cfg(target_os = "android")]
pub fn corner_radius_bottom_px() -> f32 {
    use jni::objects::{JObject, JValue};
    use jni::sys::{jobject, JavaVM as RawVm};
    use jni::JavaVM;

    let Some((vm, act)) = HANDLES.get().copied() else {
        return 0.0;
    };
    unsafe {
        let Ok(vm) = JavaVM::from_raw(vm as *mut RawVm) else {
            return 0.0;
        };
        let Ok(mut env) = vm.attach_current_thread() else {
            return 0.0;
        };
        let activity = JObject::from_raw(act as jobject);
        // Activity.getDisplay()（API 30+）；null 说明当前没有显示
        let Ok(disp) = env.call_method(&activity, "getDisplay", "()Landroid/view/Display;", &[]) else {
            return 0.0;
        };
        let Ok(disp) = disp.l() else { return 0.0 };
        if disp.is_null() {
            return 0.0;
        }
        // RoundedCorner.POSITION_BOTTOM_LEFT = 3
        let Ok(rc) = env.call_method(
            &disp,
            "getRoundedCorner",
            "(I)Landroid/view/RoundedCorner;",
            &[JValue::Int(3)],
        ) else {
            return 0.0;
        };
        let Ok(rc) = rc.l() else { return 0.0 };
        if rc.is_null() {
            return 0.0;
        }
        let Ok(r) = env.call_method(&rc, "getRadius", "()I", &[]) else {
            return 0.0;
        };
        let Ok(r) = r.i() else { return 0.0 };
        r.max(0) as f32
    }
}

#[cfg(not(target_os = "android"))]
pub fn corner_radius_bottom_px() -> f32 {
    0.0
}

/// 请求屏幕刷新率（Android 11 / API 30+ 的 `Window.setFrameRate`）。
/// 转盘的时候要 120Hz，静止时会自动降下来。
#[cfg(target_os = "android")]
pub fn set_frame_rate(fps: f32) -> bool {
    // 必须走 NDK：android_main 不在 UI 线程上，Window.setFrameRate / setAttributes
    // 都会抛 JavaException。ANativeWindow_setFrameRate 是 SurfaceControl 的封装，
    // 任何线程都能调。
    use std::ffi::{c_char, c_int, c_void, CString};

    extern "C" {
        fn dlopen(filename: *const c_char, flag: c_int) -> *mut c_void;
        fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
    }
    const RTLD_NOW: c_int = 2;

    type SetRate = unsafe extern "C" fn(*mut c_void, f32, i8) -> c_int;

    let Some(app) = APP.get() else {
        return false;
    };
    let Some(win) = app.native_window() else {
        return false; // surface 还没建好，下次再试
    };
    let ptr = win.ptr().as_ptr() as *mut c_void;
    if ptr.is_null() {
        return false;
    }
    unsafe {
        let Ok(lib) = CString::new("libandroid.so") else {
            return false;
        };
        let h = dlopen(lib.as_ptr(), RTLD_NOW);
        if h.is_null() {
            return false;
        }

        // 优先用"带切换策略"的版本：策略 = CHANGE_FRAME_RATE_ALWAYS(1)。
        // 之前用的是 ONLY_IF_SEAMLESS(0)，系统判定 60→120 不是无缝切换就直接不理，
        // 所以面板一直停在 60Hz（dumpsys display 里 mActiveModeId 一直是 60）。
        let sym2 = CString::new("ANativeWindow_setFrameRateWithChangeStrategy").ok();
        if let Some(sym2) = sym2 {
            let f2 = dlsym(h, sym2.as_ptr());
            if !f2.is_null() {
                let g: unsafe extern "C" fn(*mut c_void, f32, i8, i8) -> c_int =
                    std::mem::transmute(f2);
                let rc = g(ptr, fps, 1, 1);
                log::info!("setFrameRateWithChangeStrategy({fps}, FIXED_SOURCE+ALWAYS) rc={rc}");
                if rc == 0 {
                    return true;
                }
            }
        }

        let Ok(sym) = CString::new("ANativeWindow_setFrameRate") else {
            return false;
        };
        let f = dlsym(h, sym.as_ptr());
        if f.is_null() {
            log::warn!("ANativeWindow_setFrameRate unavailable");
            return false;
        }
        let g: SetRate = std::mem::transmute(f);
        let rc = g(ptr, fps, 0);
        log::info!("ANativeWindow_setFrameRate({fps}) rc={rc}");
        rc == 0
    }
}

/// 桌面端没有刷新率可请求
#[cfg(not(target_os = "android"))]
pub fn set_frame_rate(_fps: f32) -> bool {
    false
}
