import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.steel101.wearsyncforbreezy"
    compileSdk = 37
    ndkVersion = "26.1.10909125"

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.steel101.wearsyncforbreezy"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.0.55"
        resConfigs("en")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = project.findProperty("ANDROID_KEYSTORE_FILE")?.toString()
                ?: System.getenv("ANDROID_KEYSTORE_FILE")
                ?: localProperties.getProperty("ANDROID_KEYSTORE_FILE")

            if (!storePath.isNullOrEmpty()) {
                val keystoreFile = file(storePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = project.findProperty("ANDROID_KEYSTORE_PASSWORD")?.toString()
                        ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
                        ?: localProperties.getProperty("ANDROID_KEYSTORE_PASSWORD")

                    keyAlias = project.findProperty("ANDROID_KEY_ALIAS")?.toString()
                        ?: System.getenv("ANDROID_KEY_ALIAS")
                        ?: localProperties.getProperty("ANDROID_KEY_ALIAS")

                    keyPassword = project.findProperty("ANDROID_KEY_PASSWORD")?.toString()
                        ?: System.getenv("ANDROID_KEY_PASSWORD")
                        ?: localProperties.getProperty("ANDROID_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile != null && releaseSigningConfig.storeFile!!.exists()) {
                signingConfig = releaseSigningConfig
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "store"
    productFlavors {
        create("googlePlay") {
            dimension = "store"
        }
        create("foss") {
            dimension = "store"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/native-image/**"
            excludes += "/META-INF/okio.kotlin_module"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.startup)

    "googlePlayImplementation"(libs.play.services.wearable)
    "googlePlayImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    "googlePlayImplementation"(libs.libadb)
    "googlePlayImplementation"(libs.libconscrypt)
    "googlePlayImplementation"(libs.libsun.security)

    "fossImplementation"(libs.hivemq.mqtt.client)
    "fossImplementation"(libs.libadb)
    "fossImplementation"(libs.libconscrypt)
    "fossImplementation"(libs.libsun.security)

    implementation(project(":shared"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
