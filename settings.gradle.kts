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
