plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bilpay.app"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.bilpay.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        val configuredApiUrl = (project.findProperty("BILPAY_API_BASE_URL") as String?)?.trim()
        val apiBaseUrl = configuredApiUrl?.takeIf { it.isNotEmpty() } ?: "https://bilpay-api-5b57.onrender.com"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
