plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

dependencies { implementation("androidx.core:core-ktx:1.13.1") }

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
        versionCode = 6
        versionName = "1.1.4"
        val configuredApiUrl = (project.findProperty("BILPAY_API_BASE_URL") as String?)?.trim()
        val apiBaseUrl = configuredApiUrl?.takeIf { it.isNotEmpty() } ?: "https://bilpay-api-5b57.onrender.com"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        getByName("release") {
            // Use the stable CI debug identity only as a temporary installable fallback.
            // A genuine production release still requires the private permanent keystore.
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
