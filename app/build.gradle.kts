plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tdc.charging"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tdc.charging"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.9"
    }

    // Fixed key in the repo, so every new APK installs over the old one
    signingConfigs {
        create("shared") {
            storeFile = file("tdc.keystore")
            storePassword = "tdcharging"
            keyAlias = "tdc"
            keyPassword = "tdcharging"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
