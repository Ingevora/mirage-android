// The sample app: one screen with both players. Debug and release build types; release is minified with the
// default optimised R8 file, the path a host's release build takes.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ingevora.mirage.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ingevora.mirage.sample"
        minSdk = 25
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // No signing config is committed: the release build signs with this machine's debug key, so it
            // installs for a try. Your app signs with its own key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.mirage)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
}
