plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" 
}

android {
    namespace = "com.steel101.wearsyncforbreezy.shared"
    compileSdk = 35
}

dependencies {
    api(libs.breezy.datasharing)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.json:json:20250107")
}
