plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android) // Uses the working, pre-mapped Kotlin core plugin
}

android {
    namespace = "com.steel101.wearsyncforbreezy.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    api(libs.breezy.datasharing)
    implementation(libs.kotlinx.serialization.json) // This handles your JSON code tasks perfectly!
    implementation("org.json:json:20250107")
}
