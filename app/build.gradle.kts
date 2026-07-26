plugins {
    id("com.android.application") // AGP 9: Kotlin support is built in
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mosman.thrum"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mosman.thrum"
        // API 31 is the floor: VibratorManager, and the haptics work this depends on.
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.4.0")

    // Score and its serialization are pure Kotlin with no Android or JSON
    // dependency, so junit alone is enough to test the whole analysis layer.
    testImplementation("junit:junit:4.13.2")
}
