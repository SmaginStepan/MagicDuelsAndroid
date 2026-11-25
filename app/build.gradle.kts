plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.an0obis.magicduel"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.an0obis.magicduel"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
