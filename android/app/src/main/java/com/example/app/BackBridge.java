package com.example.app;

import android.app.Activity;
import android.os.Build;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * 预测性返回的最小桥：只做平台回调 → native 的转发，不含任何业务逻辑。
 *
 * <p>Android 14+ 用 {@link OnBackAnimationCallback}，系统在手势进行中持续回调
 * {@code onBackStarted/onBackProgressed}（0..1 进度）；Rust 侧用 {@code crate::back}
 * 的原子变量接收，直接驱动页面位移逐帧跟手。Android 13 只拿得到最终提交。
 */
public final class BackBridge {
    private static Object cb;

    private BackBridge() {}

    /** 幂等；只在 API 33+ 有 OnBackInvokedDispatcher。 */
    public static void install(Activity activity) {
        if (Build.VERSION.SDK_INT < 33 || cb != null) {
            return;
        }
        OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
        if (Build.VERSION.SDK_INT >= 34) {
            OnBackAnimationCallback anim = new OnBackAnimationCallback() {
                @Override
                public void onBackStarted(BackEvent e) {
                    nativeStarted(e.getProgress());
                }

                @Override
                public void onBackProgressed(BackEvent e) {
                    nativeProgress(e.getProgress());
                }

                @Override
                public void onBackCancelled() {
                    nativeCancelled();
                }

                @Override
                public void onBackInvoked() {
                    nativeInvoked();
                }
            };
            cb = anim;
            dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, anim);
        } else {
            OnBackInvokedCallback plain = new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() {
                    nativeInvoked();
                }
            };
            cb = plain;
            dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, plain);
        }
    }

    private static native void nativeStarted(float progress);

    private static native void nativeProgress(float progress);

    private static native void nativeCancelled();

    private static native void nativeInvoked();
}
