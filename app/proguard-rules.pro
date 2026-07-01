# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Defensive Engineering Keeps ---

# Prevent Room entities, DAOs, and Database classes from being renamed or stripped
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * {
    @androidx.room.Dao *;
}
-keepclassmembers class * {
    @androidx.room.Database *;
}

# Keep our data entities and DB model structures intact for Serialization/Deserialization
-keep class com.example.data.local.** { *; }

# Prevent obfuscation of our security methods so they stacktrace clearly if needed, 
# but allow renaming of internal validation fields to confuse static analysis tools.
-keepclassmembers class com.example.ui.viewmodel.FinanceViewModel {
    *** isTrialExpired(...);
    *** activateLicense(...);
}

# --- Compose and UI State Optimizations ---
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.compose.runtime.Immutable <fields>;
    @androidx.compose.runtime.Stable <fields>;
}

# Keep our UI state structures as they represent model models in our view levels
-keep class com.example.ui.state.** { *; }

# OkHttp Platform rules
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

