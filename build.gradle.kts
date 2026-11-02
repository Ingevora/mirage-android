// The sample build's root: the plugin versions, from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied: AGP 9 compiles Kotlin itself. Declared only to pin the Kotlin version it uses.
    alias(libs.plugins.kotlin.android) apply false
}
