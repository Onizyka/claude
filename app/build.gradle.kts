plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Версия приложения. Повышается при каждом изменении (см. CHANGELOG.md):
 * новая функция — средняя цифра (2.1.0 → 2.2.0), исправление — последняя (2.1.0 → 2.1.1).
 */
val appVersion = "2.2.0"

/** 2.1.0 → 20100: код версии растёт вместе с номером. */
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.split(".").map { it.toInt() }
    return major * 10000 + minor * 100 + patch
}

android {
    namespace = "ru.inventory.dc"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.inventory.dc"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    // Ключ подписи передаётся сборке через переменные окружения (в GitHub — из секретов),
    // в репозитории его нет.
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
            isMinifyEnabled = false
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Сканирование QR-кодов и штрихкодов (работает без Google Play Services)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Отправка почты по SMTP
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
