import java.util.Properties
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
        versionCode = 3
        versionName = "0.3.0"
        // Phones + emulators. Dropping x86 keeps the APK small.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // Release signing comes from environment variables (CI secrets) or an untracked
    // keystore.properties. Without either, the release APK is left unsigned, which is
    // what F-Droid expects: it builds from source and signs with its own key.
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(env: String, prop: String): String? =
        System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
    val storePath = signingValue("MM_KEYSTORE_FILE", "storeFile")

    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("MM_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("MM_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("MM_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // F-Droid rejects the encrypted dependency metadata block Google adds to APKs.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
    // MapLibre's HTTP client, used directly to tidy up aerial photo tiles (AerialTiles.kt).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.mikepenz:aboutlibraries-core:12.2.4")
}
