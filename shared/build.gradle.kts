plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization") // FIXED: Using direct ID string format
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
    implementation(libs.kotlinx.serialization.json)
    implementation("org.json:json:20250107")
}
