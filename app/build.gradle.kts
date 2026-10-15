import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// ---------------------------------------------------------------------------
// Secrets and private configuration stay in local.properties (git-ignored).
// They reach the Java code through BuildConfig, never through source files.
// ---------------------------------------------------------------------------
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localValue(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: default).trim()

fun localFlag(name: String): String =
    if (localValue(name, "false").equals("true", ignoreCase = true)) "true" else "false"

// The Google Services plugin needs app/google-services.json (downloaded from your own
// Firebase project). It is applied only when the file exists so the project still builds
// on a fresh clone; the app then shows a "Firebase is not configured" message.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.rutarescue"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.rutarescue"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TOMTOM_API_KEY", "\"${localValue("TOMTOM_API_KEY")}\"")
        buildConfigField("boolean", "USE_FIREBASE_STORAGE", localFlag("USE_FIREBASE_STORAGE"))
        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", localFlag("USE_FIREBASE_EMULATOR"))
        buildConfigField("String", "EMULATOR_HOST", "\"${localValue("EMULATOR_HOST", "10.0.2.2")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Lets pure-Java unit tests call android.util.Log (it returns default values).
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AndroidX + Material Components (XML views)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.runtime)

    // Firebase: Authentication, Cloud Firestore (with offline cache) and Storage
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Map rendering, JSON parsing and QR codes
    implementation(libs.maplibre.android)
    implementation(libs.gson)
    implementation(libs.zxing.android.embedded)

    // Local (JVM) unit tests for the pure-Java model, validation, persistence and risk engine
    testImplementation(libs.junit)
    testImplementation(libs.gson)

    // Instrumented tests (security rules against the Firebase Emulator Suite)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
