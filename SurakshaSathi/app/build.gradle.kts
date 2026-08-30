import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// ── Read local.properties safely ──────────────────────────────────────────────
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun localProp(key: String, default: String = "") =
    localProps.getProperty(key, default)

android {
    namespace = "com.sbi.surakshasathi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sbi.surakshasathi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig fields — never hard-code secrets in source
        buildConfigField("String", "BACKEND_BASE_URL",
            "\"${localProp("BACKEND_BASE_URL", "https://api.surakshasathi.bank.example/")}\"")
        buildConfigField("String", "MAPS_API_KEY",
            "\"${localProp("MAPS_API_KEY", "")}\"")
        buildConfigField("String", "VIRUSTOTAL_API_KEY",
            "\"${localProp("VIRUSTOTAL_API_KEY", "")}\"")
        buildConfigField("Long", "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER",
            "${localProp("PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "0")}L")
        buildConfigField("Boolean", "USE_OFFLINE_FALLBACK",
            localProp("USE_OFFLINE_FALLBACK", "false"))

        // Azure Translator (multi-language Learn content) — empty by default; translated text
        // transparently falls back to the original English source until a key is supplied.
        buildConfigField("String", "AZURE_TRANSLATOR_KEY",
            "\"${localProp("AZURE_TRANSLATOR_KEY", "")}\"")
        buildConfigField("String", "AZURE_TRANSLATOR_REGION",
            "\"${localProp("AZURE_TRANSLATOR_REGION", "")}\"")
        buildConfigField("String", "AZURE_TRANSLATOR_ENDPOINT",
            "\"${localProp("AZURE_TRANSLATOR_ENDPOINT", "https://api.cognitive.microsofttranslator.com/")}\"")

        // Manifest placeholders for API keys
        manifestPlaceholders["MAPS_API_KEY"] = localProp("MAPS_API_KEY", "")
    }

    // ── SMS strategy flavors (§1.2) ─────────────────────────────────────────
    // SMS_STRATEGY is a FLAVOR dimension, not a local.properties toggle, on
    // purpose: it keeps the runtime strategy and the manifest's restricted-
    // permission declarations atomically in sync. "notificationOnly" NEVER
    // declares READ_SMS/RECEIVE_SMS (see src/main/AndroidManifest.xml), so it
    // is Play-publishable with no restricted-permission review. "defaultHandler"
    // pulls those permissions in via src/defaultHandler/AndroidManifest.xml and
    // requires the Play Permissions Declaration Form, or enterprise/sideload
    // distribution.
    flavorDimensions += "smsStrategy"
    productFlavors {
        create("notificationOnly") {
            dimension = "smsStrategy"
            buildConfigField("String", "SMS_STRATEGY", "\"NOTIFICATION_ONLY\"")
        }
        create("defaultHandler") {
            dimension = "smsStrategy"
            applicationIdSuffix = ".defaulthandler"
            buildConfigField("String", "SMS_STRATEGY", "\"DEFAULT_HANDLER\"")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = localProp("KEYSTORE_PATH").let { if (it.isNotEmpty()) file(it) else null }
            storePassword = localProp("KEYSTORE_PASSWORD")
            keyAlias = localProp("KEY_ALIAS")
            keyPassword = localProp("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    // App Bundle support
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    // 16 KB memory-page-size compliance (Play Store requirement)
    androidResources {
        generateLocaleConfig = true
        // AAPT compresses assets by default, but the TFLite Interpreter memory-maps the model
        // file directly -- without this, loading spam_classifier_mobile.tflite throws
        // FileNotFoundException ("probably compressed") and the real model silently never loads.
        // (This exact bug previously hit the .ptl PyTorch Mobile asset for the same reason.)
        noCompress += "tflite"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // ── Core AndroidX ──────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.biometric)

    // ── Compose ───────────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.accompanist.permissions)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Kotlin ────────────────────────────────────────────────────────────────
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.serialization.json)

    // ── Hilt DI ───────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)

    // ── Room + SQLCipher (encrypted) ──────────────────────────────────────────
    implementation(libs.bundles.room)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)
    ksp(libs.room.compiler)

    // ── DataStore (encrypted) ─────────────────────────────────────────────────
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // ── Networking ────────────────────────────────────────────────────────────
    implementation(libs.bundles.retrofit)

    // ── WorkManager ───────────────────────────────────────────────────────────
    implementation(libs.androidx.work.runtime)

    // ── TensorFlow Lite (on-device ML) ──────────────────────────────────────────
    implementation(libs.tensorflow.lite)

    // ── ML Kit ────────────────────────────────────────────────────────────────
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.barcode.scanning)

    // ── CameraX ───────────────────────────────────────────────────────────────
    implementation(libs.bundles.camerax)

    // ── Google Maps ───────────────────────────────────────────────────────────
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // ── Firebase ──────────────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // ── Play Integrity ────────────────────────────────────────────────────────
    implementation(libs.play.integrity)

    // ── Media3 (video nudges) ─────────────────────────────────────────────────
    implementation(libs.bundles.media3)

    // ── Image loading ─────────────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Logging (planted in debug only — never logs in release, §8C) ───────────
    implementation(libs.timber)

    // ── Debug tools ───────────────────────────────────────────────────────────
    debugImplementation(libs.leakcanary)

    // ── Unit Tests ────────────────────────────────────────────────────────────
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.junit5.engine)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)

    // ── Instrumented / UI Tests ───────────────────────────────────────────────
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.mockk)
}

// ── JUnit5 support for Android unit tests ─────────────────────────────────────
tasks.withType<Test> {
    useJUnitPlatform()
}
