# Lumen 的 R8 规则
#
# 纯 Rust 应用，Java 侧只有 androidx.games:games-activity（GameActivity）及其依赖。
# GameActivity 通过 JNI 调用 libapp.so 里的 android_main / initializeNativeCode，
# 这些符号由 Rust 侧 #[no_mangle] 导出，Java 侧方法名必须原样保留。

# 保留 GameActivity 及其 JNI 入口（native 方法默认保留，这里显式声明更稳）
-keep class com.google.androidgamesdk.GameActivity { *; }
-keep class com.google.androidgamesdk.gameactivity.** { *; }

# 保留 JNI native 方法（防止方法名被混淆后 JNI 找不到）
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留注解（AppCompat 等依赖可能依赖反射）
-keepattributes *Annotation*
