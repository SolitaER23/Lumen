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

# TensorFlow Lite (GPU / XNNPACK delegates use JNI class lookups)
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**
# ONNX Runtime JNI
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# WorkManager reflective worker construction
-keep class app.lumen.player.playback.MoodWorker { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
