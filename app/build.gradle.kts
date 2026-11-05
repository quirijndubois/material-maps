import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Generates the list of bundled libraries and their licenses for the credits screen.
    id("com.mikepenz.aboutlibraries.plugin")
}

android {
    namespace = "app.materialmaps"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.materialmaps"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Phones + emulators. Dropping x86 keeps the APK small.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // One shared key so every build installs over the previous one.
    // Fine for a personal sideloaded app; use a secret keystore before publishing to a store.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/materialmaps.jks")
            storePassword = "materialmaps"
            keyAlias = "materialmaps"
            keyPassword = "materialmaps"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    implementation("com.mikepenz:aboutlibraries-core:12.2.4")
}
