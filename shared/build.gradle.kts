plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.steel101.wearsyncforbreezy.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("int", "VERSION_CODE", "27")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.breezy.datasharing)
    implementation(libs.kotlinx.serialization.json)
}
