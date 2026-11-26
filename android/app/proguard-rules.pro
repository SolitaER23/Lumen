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
# 注意：不要整层 keep app.lumen.player.data.**
# 该包没有任何 Gson 反射目标（只用 JsonObject）也没有 @Serializable 类，
# 唯一需要保留的是 Retrofit 接口（见上面的 SubsonicApi）。
# 保留整包会让 R8 失去混淆与裁剪能力，也让"哪些成员真被反射使用"变得不可知。

# Kotlinx serialization
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }
