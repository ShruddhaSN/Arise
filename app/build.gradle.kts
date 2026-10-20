import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.arise.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arise.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    // Fixed signing key so every new build installs as an update (your data stays).
    signingConfigs {
        create("arise") {
            storeFile = file("arise.keystore")
            storePassword = "arisearise"
            keyAlias = "arise"
            keyPassword = "arisearise"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("arise")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("arise")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.glance:glance-appwidget:1.1.1")
}
