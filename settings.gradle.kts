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
        google()
        mavenCentral()
    }
}

// Macrobenchmarks and the Baseline Profile generator for :app (they run on a device).
include(":baselineprofile")

rootProject.name = "safar"
include(":protocol", ":app", ":bikesim")

// Local-only debug APK (cluster icon audit, live Maps capture); git-ignored, so clones build without it.
if (file("navtest").exists()) include(":navtest")
