plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.kouwis.alzkr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kouwis.alzkr"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        // the Google Play build (-Pplay): shares the store's link, and has no support-by-transfer button (Play's payments rules)
        buildConfigField("boolean", "PLAY", if (project.hasProperty("play")) "true" else "false")
    }

    signingConfigs {
        // Test-phase key, committed on purpose so every CI build can update the
        // previous install. Replace with a private upload key before Google Play.
        create("test") {
            storeFile = file("test.keystore")
            storePassword = "alzkrtest"
            keyAlias = "alzkr"
            keyPassword = "alzkrtest"
        }

        // Google Play: the private upload key, given to CI as secrets (never committed). Used only for
        // the bundle built with -Pplay (the APK on GitHub keeps the test key, so installs keep updating).
        val ks = System.getenv("UPLOAD_KEYSTORE")
        if (ks != null && file(ks).exists()) create("upload") {
            storeFile = file(ks)
            storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName(if (project.hasProperty("play")) "upload" else "test") ?: signingConfigs.getByName("test")
        }
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // home-screen widgets («الودجت»)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
