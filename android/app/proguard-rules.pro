# Retrofit / Gson
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep interface app.lumen.player.data.SubsonicApi { *; }
-keep class com.google.gson.** { *; }
-keep class app.lumen.player.data.** { *; }

# Kotlinx serialization
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }

# ONNX Runtime：R8 混淆会移除 JNI 反射用到的类/方法名，
# 导致原生层 GetMethodID 拿不到 java_class 而 abort（release 崩溃的根因）。
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
