# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# R8 Full Mode is enabled in gradle.properties (android.enableR8.fullMode=true)

# Data models used for Room and JSON serialization
-keep class com.matrix.devlog.data.** { *; }
-keep class com.matrix.devlog.practice.PracticeProblem { *; }

# Room
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keepattributes *Annotation*

# Moshi - Keep JsonAdapter logic
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Retrofit - Standard attributes for reflection
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Optimization settings
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively

# Remove Log calls in optimized builds (debug, verbose, info, warn)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
