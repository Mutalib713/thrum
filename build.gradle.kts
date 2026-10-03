plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP 9 has built-in Kotlin — org.jetbrains.kotlin.android must NOT be applied.
    // Compose still needs its compiler plugin:
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    // Task 17: Room needs its annotation processor at build time. The
    // Kotlin-matched KSP line (2.2.20-2.0.4) rejects AGP 9's built-in Kotlin
    // outright — "KSP is not compatible with Android Gradle Plugin's built-in
    // Kotlin" — but the newer 2.3.x scheme accepts it. 2.3.12 compiles and
    // generates code with this project's arrangement, proven 2026-10-03; see
    // CLAUDE.md machine gotchas before bumping either side of this pair.
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
