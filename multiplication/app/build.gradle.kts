plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Версия приложения: новая функция — средняя цифра, исправление — последняя. */
val appVersion = "1.1.0"

/** 1.2.3 → 10203: код версии растёт вместе с номером. */
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.split(".").map { it.toInt() }
    return major * 10000 + minor * 100 + patch
}

android {
    namespace = "ru.kids.multiply"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.kids.multiply"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    // Ключ подписи передаётся через переменные окружения (в GitHub — из секретов).
    signingConfigs {
        val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
