plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.civic.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.civic.launcher"
        minSdk = 17          // Honda Display Audio = Android 4.2.2
        targetSdk = 22       // install-time permissions, no runtime prompts
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    lint { abortOnError = false; disable += "ExpiredTargetSdkVersion" }
}

// Intentionally zero dependencies besides the Kotlin stdlib.
