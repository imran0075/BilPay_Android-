plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}

android {
    namespace = "com.bilpay.app"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("BILPAY_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storeType = System.getenv("BILPAY_KEYSTORE_TYPE")
                storePassword = System.getenv("BILPAY_STORE_PASSWORD")
                keyAlias = System.getenv("BILPAY_KEY_ALIAS")
                keyPassword = System.getenv("BILPAY_KEY_PASSWORD")
            }
        }
    }

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.bilpay.mobile"
        minSdk = 23
        targetSdk = 35
        versionCode = 24
        versionName = "1.4.1"
        val configuredApiUrl = (project.findProperty("BILPAY_API_BASE_URL") as String?)?.trim()
        val apiBaseUrl = configuredApiUrl?.takeIf { it.isNotEmpty() } ?: "https://bilpay-sandbox-api-v2.onrender.com"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".staging"
            buildConfigField("String", "API_BASE_URL", "\"https://bilpay-sandbox-api-v2.onrender.com\"")
        }
        getByName("release") {
            val permanentKeyConfigured = !System.getenv("BILPAY_KEYSTORE_PATH").isNullOrBlank()
            signingConfig = if (permanentKeyConfigured) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}
