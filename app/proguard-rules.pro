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

# ===== Building Management App ProGuard Rules =====

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== FIREBASE RULES =====
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.auth.internal.** { *; }

# Firebase Database
-keep class com.google.firebase.database.** { *; }
-keep class com.firebase.** { *; }
-keep class org.apache.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }

# Firebase Messaging (FCM)
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }

# Firebase App Check
-keep class com.google.firebase.appcheck.** { *; }

# ===== WEBVIEW & JAVASCRIPT =====
# Keep WebView JavaScript interfaces (cho WebPayActivity)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView related classes
-keep class android.webkit.** { *; }
-keep class **.R$* { *; }

# ===== KOTLIN & COROUTINES =====
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }

# ===== OKHTTP (cho network calls) =====
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ===== GSON/JSON (nếu có) =====
-keep class com.google.gson.** { *; }
-keep class org.json.** { *; }

# ===== APP SPECIFIC RULES =====
# Keep main Application class
-keep class com.app.buildingmanagement.BuildingManagementApplication { *; }

# Keep FCM Service
-keep class com.app.buildingmanagement.firebase.FCMService { *; }
-keep class com.app.buildingmanagement.firebase.FCMHelper { *; }

# Keep data models (cho Firebase serialization)
-keep class com.app.buildingmanagement.model.** { *; }

# Keep Activities (đặc biệt WebPayActivity có deep links)
-keep class com.app.buildingmanagement.*Activity { *; }

# Keep Fragment classes
-keep class com.app.buildingmanagement.fragment.** { *; }

# Keep adapter classes
-keep class com.app.buildingmanagement.adapter.** { *; }

# Keep data classes
-keep class com.app.buildingmanagement.data.** { *; }

# ===== ANDROID COMPONENTS =====
# Keep all View constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep custom views
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== CHART LIBRARY (MPAndroidChart) =====
-keep class com.github.mikephil.charting.** { *; }

# ===== REMOVE LOGS IN RELEASE =====
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# ===== OPTIMIZATION =====
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# ===== ADDITIONAL SAFETY RULES =====
# Keep Serializable classes
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}