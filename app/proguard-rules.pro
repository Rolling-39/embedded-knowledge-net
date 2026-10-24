# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.rolling.learningguider.**$$serializer { *; }
-keepclassmembers class com.rolling.learningguider.** { *** Companion; }
-keepclasseswithmembers class com.rolling.learningguider.** { kotlinx.serialization.KSerializer serializer(...); }
