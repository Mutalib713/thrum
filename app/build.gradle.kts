// Imported rather than written as `java.util.Properties()`: inside a Kotlin build
// script `java` resolves to the Java plugin's extension, not the package, so the
// fully-qualified form fails to compile.
import java.util.Properties

plugins {
    id("com.android.application") // AGP 9: Kotlin support is built in
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") // Room's annotation processor (Task 17)
}

/**
 * Task 12. The release signing key, read from a file that is git-ignored.
 *
 * Deliberately tolerant of that file being absent. A fresh clone has no keystore
 * — it is a secret, and secrets do not travel in git — so a release build there
 * falls back to the debug key instead of failing. That keeps `assembleRelease`
 * usable by anyone who wants to check that R8 has not broken the serialisation,
 * which is a thing worth being able to check without holding the signing key.
 *
 * The fallback is not shippable, and it cannot quietly become shippable: Play
 * rejects a debug-signed upload at the door. See `CLAUDE.md` for the backup
 * warning — losing this file means never being able to update the app again.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseKey = keystoreProps.getProperty("storeFile")

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

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = rootProject.file(releaseKey)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 on, because a release build is the only place the shrinker's
            // enum-renaming bug can appear — `proguard-rules.pro` carries the rule
            // that stops it, and a rule that is never exercised is a rule nobody
            // knows is wrong. The full story is in `CLAUDE.md`, machine gotchas.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Task 12: the real key when it is present, the debug key when it is
            // not. Task 8 needed the debug key here specifically so a release
            // build would install over a debug one and prove saved data survives
            // shrinking; that is done, and it is no longer a reason to ship a
            // debug-signed artifact.
            signingConfig = if (releaseKey != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    // Task 17: the haptic library. Mutalib picked Room over plain files —
    // hundreds of haptics need lists and search, which is what a database is
    // for (PROFILE §7). Version read off Google's Maven metadata, not guessed.
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Score and its serialization are pure Kotlin with no Android or JSON
    // dependency, so junit alone is enough to test the whole analysis layer.
    testImplementation("junit:junit:4.13.2")
}
