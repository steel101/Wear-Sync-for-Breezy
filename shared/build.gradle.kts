plugins {
    id("com.android.library")
    kotlin("android") apply false
}

android {
    namespace = "com.steel101.wearsyncforbreezy.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("org.json:json:20250107")
}
