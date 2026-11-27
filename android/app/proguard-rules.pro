# ---------------------------------------------------------------------------
# 这个文件只保留「本应用独有、库规则覆盖不到」的 keep。
# 每一条都写清为什么需要；没有理由的一律删掉，因为整包 keep 会直接把
# 裁剪/混淆/优化三项收益一起吃掉（见 R8 Configuration Analyzer 的 blast radius）。
# ---------------------------------------------------------------------------

# Gson / Retrofit 都靠注解与泛型签名做反射解析，这两行属性必须保留。
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Retrofit (2.x/3.x) 的 AAR 自带 consumer 规则 retrofit2.pro，已经覆盖：
#   -if interface * { @retrofit2.http.* <methods>; } -keep,allowobfuscation interface <1>
#   -keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
#   -keep,allowobfuscation,allowshrinking class retrofit2.Response
#   -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# 所以下面这些手写规则全部是重复的，且 "-keep interface SubsonicApi { *; }" 会让整个
# 接口（含所有方法体引用）失去裁剪与混淆能力：
#   -keep,allowobfuscation interface retrofit2.Call
#   -keep,allowobfuscation class retrofit2.Response
#   -keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
#   -keep interface app.lumen.player.data.SubsonicApi { *; }
# 已删除（R8 报告里它们是互相 subsumed 的重复项）。

# Gson 2.10+ 自带 consumer 规则 gson.pro，覆盖 @SerializedName、@Expose、
# @JsonAdapter、TypeToken、JsonSerializer/JsonDeserializer、TypeAdapterFactory。
# 本应用只在 data/Subsonic.kt 里用 com.google.gson.JsonObject 作为 Retrofit 的
# 返回类型（之后交给 org.json 解析），没有任何「字段级反射反序列化」的模型类，
# 因此不需要 "-keep class com.google.gson.** { *; }"（该规则曾单独锁住 1949 个
# class/field/method，占可优化代码的 1.55%）。

# 注意：不要整层 keep app.lumen.player.data.**
# 该包没有 Gson 反射目标（只用 JsonObject），唯一需要反射签名的是 Retrofit 接口，
# 已由 retrofit2.pro 的注解规则覆盖。

# Kotlinx serialization
# kotlinx-serialization-core-jvm:1.13.0 自带 kotlinx-serialization-r8.pro 与
# kotlinx-serialization-common.pro，已覆盖 @Serializable 类本体、$$serializer、
# INSTANCE、Companion、descriptor 以及 throw*Exception 辅助方法。
# 本应用只有一个可序列化模型（data/Album，供 AlbumCache 落盘），
# 因此不需要 "-keep class kotlinx.serialization.** { *; }"
# （该规则曾单独锁住 3648 个 class/field/method，占可优化代码的 2.91%）。
-keepattributes RuntimeVisibleAnnotations
