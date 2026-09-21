plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.musickeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.musickeyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // API anahtarını BuildConfig'e güvenli şekilde gömüyoruz
        // local.properties dosyasına MUSIC_API_KEY=xxx şeklinde ekle
        val musicApiKey: String = project.findProperty("MUSIC_API_KEY") as String? ?: "YOUR_API_KEY_HERE"
        buildConfigField("String", "MUSIC_API_KEY", "\"$musicApiKey\"")
        buildConfigField("String", "MUSIC_API_BASE_URL", "\"https://your-legal-api.example.com/\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
