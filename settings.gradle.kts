// The sample's own build (packaging spec decision 11), the counterpart of the iOS SDK's Distribution/Package.swift.
// In the public repo Ingevora/mirage-android this is the root build: clone it, open it in Android Studio, run
// `sample`. In the private repo it lives in Distribution/ and builds with the root's wrapper:
//   ./gradlew -p Distribution :sample:assembleDebug
// It knows the SDK only as com.ingevora:mirage from the maven/ folder beside this file: the published repository in
// the public repo, the git-ignored copy scripts/make-release.sh leaves in the private repo. A host app uses the
// GitHub Pages URL instead (README, Install).
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // com.ingevora comes from maven/ beside this file and from nowhere else.
        exclusiveContent {
            forRepository {
                maven {
                    name = "mirage"
                    url = settingsDir.resolve("maven").toURI()
                }
            }
            filter { includeGroup("com.ingevora") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "mirage-android"
include(":sample")
