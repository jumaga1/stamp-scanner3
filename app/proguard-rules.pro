# Room
-keep class androidx.room.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.filatelia.scanner.**$$serializer { *; }
-keepclassmembers class com.filatelia.scanner.** { *** Companion; }
-keepclasseswithmembers class com.filatelia.scanner.** { kotlinx.serialization.KSerializer serializer(...); }
