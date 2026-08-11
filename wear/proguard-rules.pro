# Wear OS ProGuard rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# Keep serialization
-keepclassmembers class com.theveloper.pixelplay.shared.** {
    *;
}

# Suppress warnings when R8 cannot parse newer Kotlin metadata formats
-dontwarn kotlin.Metadata
