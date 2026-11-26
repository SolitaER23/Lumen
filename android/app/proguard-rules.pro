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
