# Wear OS 移植版混淆规则：仅保留 kotlinx.serialization 的序列化器。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class moe.yzf.comic.wear.data.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class moe.yzf.comic.wear.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
