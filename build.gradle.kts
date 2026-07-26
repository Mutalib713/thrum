plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP 9 has built-in Kotlin — org.jetbrains.kotlin.android must NOT be applied.
    // Compose still needs its compiler plugin:
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
