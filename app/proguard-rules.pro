# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class az.magaza.pos.**$$serializer { *; }
-keepclassmembers class az.magaza.pos.** { *** Companion; }
-keepclasseswithmembers class az.magaza.pos.** { kotlinx.serialization.KSerializer serializer(...); }

# ZXing
-keep class com.journeyapps.** { *; }
-keep class com.google.zxing.** { *; }
