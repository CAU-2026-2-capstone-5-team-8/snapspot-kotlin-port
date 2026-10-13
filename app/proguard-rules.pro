# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.capstone.snapspot.**$$serializer { *; }
-keepclassmembers class com.capstone.snapspot.** { *** Companion; }
-keepclasseswithmembers class com.capstone.snapspot.** { kotlinx.serialization.KSerializer serializer(...); }

# Kakao map SDK: its native code looks up Java classes and fields by name, so R8 must not rename or remove them.
# (Without this the release build aborts at start: NoSuchFieldError ... MapViewHolder.)
-keep class com.kakao.vectormap.** { *; }
-keep interface com.kakao.vectormap.** { *; }
