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

    buildTypes {
        release {
            // R8 on, because a release build is the only place the enum-renaming
            // bug in PROFILE.md R8 can appear — and a rule that is never
            // exercised is a rule nobody knows is wrong.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the debug key **for now**, so a release build installs
            // over a debug one and Task 8 can prove that saved data survives
            // shrinking. A different key would force an uninstall, which wipes
            // the very data being tested. The real keystore is Task 12.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The diagnostics probe is reachable in debug builds only.
        buildConfig = true
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
