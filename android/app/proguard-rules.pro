# Retrofit / Gson
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep interface com.example.app.data.SubsonicApi { *; }
-keep class com.google.gson.** { *; }
-keep class com.example.app.data.** { *; }

# Kotlinx serialization
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }

# TensorFlow Lite (GPU / XNNPACK delegates use JNI class lookups)
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**