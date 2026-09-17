# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ─────────────────────────────────────────────────────────────────────────────
# 1. BASIC ANDROID KEEPS
# ─────────────────────────────────────────────────────────────────────────────

# Keep line numbers for debuggable stack traces (always safe)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all annotations (critical for AndroidX, Retrofit, Room etc.)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep R classes and their inner id/string/color/... fields
-keep class **.R$* {
    public static final <fields>;
}
-keep class **.R { *; }

# Keep Android View constructors used by LayoutInflater (XML inflation)
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}

# Keep Activity/Fragment/Service/BroadcastReceiver lifecycle classes
-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# Keep SQLiteOpenHelper subclasses and their column/table string constants
-keep class * extends android.database.sqlite.SQLiteOpenHelper {
    public <init>(android.content.Context, java.lang.String, android.database.sqlite.SQLiteDatabase$CursorFactory, int);
    public static final java.lang.String TABLE_*;
    public static final java.lang.String COLUMN_*;
    public static final int DATABASE_VERSION;
    public static final java.lang.String DATABASE_NAME;
}

# Keep Solvly DbHelper data model (nested HistoryItem used in Cursor adapters)
-keep class com.example.solvly.utils.HistoryDbHelper$HistoryItem {
    <fields>;
    <init>(...);
}

# Keep Parcelable and Serializable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
    public static final android.os.Parcelable$ClassLoaderCreator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enum values (used in settings)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─────────────────────────────────────────────────────────────────────────────
# 2. JSON / ORG.JSON (problem steps stored as JSONArrays)
# ─────────────────────────────────────────────────────────────────────────────

-keep class org.json.** { *; }
-dontwarn org.json.**

# ─────────────────────────────────────────────────────────────────────────────
# 3. ANDROIDX LIBRARIES
# ─────────────────────────────────────────────────────────────────────────────

# AndroidX Core / FileProvider
-keep class androidx.core.content.FileProvider { *; }
-keep class androidx.core.** { *; }
-dontwarn androidx.**

# AndroidX Lifecycle / ViewModel (common crash if stripped)
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# RecyclerView and its internal ItemTouchHelper (swipe-to-delete relies on it)
-keep class androidx.recyclerview.widget.** { *; }
-keep class androidx.recyclerview.widget.RecyclerView$OnItemTouchListener
-keep class androidx.recyclerview.widget.ItemTouchHelper$* { *; }

# ConstraintLayout
-keep class androidx.constraintlayout.** { *; }

# NestedScrollView / ViewPager etc
-keep class androidx.core.widget.** { *; }

# ─────────────────────────────────────────────────────────────────────────────
# 4. MATERIAL 3 COMPONENTS (Cards, FAB, Buttons, Dialogs, BottomSheets)
# ─────────────────────────────────────────────────────────────────────────────

-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keep class com.google.android.material.card.MaterialCardView { *; }
-keep class com.google.android.material.bottomsheet.BottomSheetDialog { *; }

# ─────────────────────────────────────────────────────────────────────────────
# 5. GOOGLE ML KIT TEXT RECOGNITION (OCR PIPELINE)
# ─────────────────────────────────────────────────────────────────────────────

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.mlkit.vision.text.** { *; }
-dontwarn com.google.android.gms.internal.**

# ─────────────────────────────────────────────────────────────────────────────
# 6. ANDROID IMAGE CROPPER (CanHub / CropImageActivity)
# ─────────────────────────────────────────────────────────────────────────────

-keep class com.canhub.cropper.** { *; }
-dontwarn com.canhub.cropper.**
-keep class com.canhub.cropper.CropImageActivity { *; }
-keep class com.canhub.cropper.CropImageOptions { *; }
-keep class com.canhub.cropper.CropImage$* { *; }

# ─────────────────────────────────────────────────────────────────────────────
# 7. SOLVLY AI SOLVER / MATH MODELS (Gemini API / internal result structures)
# ─────────────────────────────────────────────────────────────────────────────

# Keep Solvly solver data structures (serialized for UI and DB)
-keep class com.example.solvly.utils.MathSolver$* {
    <fields>;
    <init>(...);
}
-keep class com.example.solvly.utils.AISolver$* { *; }
-keep class com.example.solvly.utils.AISolver { *; }
-keep class com.example.solvly.utils.MathSolver { *; }

# Keep callback interfaces (used across threads)
-keep interface com.example.solvly.utils.AISolver$AISolverCallback { *; }

# Solvly utility classes
-keep class com.example.solvly.utils.** { *; }
-dontwarn com.example.solvly.utils.**

# ─────────────────────────────────────────────────────────────────────────────
# 8. OKHTTP / NETWORKING (Gemini REST client uses HttpURLConnection mostly, be safe)
# ─────────────────────────────────────────────────────────────────────────────

-keep class java.net.HttpURLConnection { *; }
-keep class javax.net.ssl.** { *; }
-dontwarn javax.net.**

# ─────────────────────────────────────────────────────────────────────────────
# 9. NOTIFICATION HELPER (BootReceiver + AlarmManager compat)
# ─────────────────────────────────────────────────────────────────────────────

-keep class com.example.solvly.utils.NotificationHelper { *; }
-keep class com.example.solvly.utils.NotificationReceiver { *; }
-keep class com.example.solvly.utils.BootReceiver { *; }

# ─────────────────────────────────────────────────────────────────────────────
# 10. BITMAP / IMAGE UTILS (decoding, compression)
# ─────────────────────────────────────────────────────────────────────────────

-keep class android.graphics.BitmapFactory { *; }
-keep class android.graphics.Bitmap { *; }
-keep class android.graphics.BitmapFactory$Options { *; }

# ─────────────────────────────────────────────────────────────────────────────
# 11. GENERIC: keep native methods and JavaScript interfaces
# ─────────────────────────────────────────────────────────────────────────────

-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
