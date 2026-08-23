plugins {
    id("com.android.application")
}

android {
    namespace = "com.chilenoapps.idepth26"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chilenoapps.idepth26"
        minSdk = 27
        targetSdk = 35
        versionCode = 4
        versionName = "1.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
