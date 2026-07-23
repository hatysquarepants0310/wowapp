# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.azeroth.companion.**$$serializer { *; }
-keepclassmembers class com.azeroth.companion.** { *** Companion; }
-keepclasseswithmembers class com.azeroth.companion.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn retrofit2.**
