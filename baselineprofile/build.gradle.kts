import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Macrobenchmark + Baseline Profile generator for :app. Needs a device or emulator (API 28+, 33+ unrooted):
//   ./gradlew :app:generateBaselineProfile                          -> app/src/main/generated/baselineProfiles
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  -> startup and Home scroll frame timing
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "app.safar.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Tells the tests which package to drive (the tested APK's id), so a suffix on a build type keeps working.
androidComponents {
    onVariants { v ->
        val loader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put("targetAppId", v.testedApks.map { loader.load(it)?.applicationId ?: "app.safar" })
    }
}
