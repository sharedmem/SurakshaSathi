# ── SurakshaSathi ProGuard / R8 Rules ─────────────────────────────────────────

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ── Strip logs in release (§8C: never log PII) ────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    # Keep e/w for crash diagnostics (no PII in these calls)
}

# ── Hilt ──────────────────────────────────────────────────────────────────────
-keepclassmembers,allowobfuscation class * {
  @javax.inject.* <fields>;
  @javax.inject.* <init>(...);
}
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# ── kotlinx.serialization ─────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.sbi.surakshasathi.**$$serializer { *; }
-keepclassmembers class com.sbi.surakshasathi.** {
    *** Companion;
}
-keepclasseswithmembers class com.sbi.surakshasathi.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Retrofit + OkHttp ────────────────────────────────────────────────────────
-keep interface com.sbi.surakshasathi.**.api.** { *; }
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-dontwarn okio.**
-dontwarn okhttp3.**

# ── TFLite ────────────────────────────────────────────────────────────────────
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ── SQLCipher ─────────────────────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }

# ── Accompanist ───────────────────────────────────────────────────────────────
-dontwarn com.google.accompanist.**

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Firebase ──────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Play Integrity ────────────────────────────────────────────────────────────
-keep class com.google.android.play.core.integrity.** { *; }

# ── Preserve BuildConfig for runtime checks ───────────────────────────────────
-keep class com.sbi.surakshasathi.BuildConfig { *; }
