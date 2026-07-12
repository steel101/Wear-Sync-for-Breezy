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

val copyGooglePlayWearApk = tasks.register<Copy>("copyGooglePlayWearApk") {
    val wearProject = project(":wear")
    dependsOn("${wearProject.path}:assembleGooglePlayRelease")
    from(wearProject.layout.buildDirectory.dir("outputs/apk/googlePlay/release"))
    include { it.name.endsWith(".apk") && !it.name.contains("-unsigned") }
    into(layout.projectDirectory.dir("src/googlePlay/assets"))
    rename { "wear_companion.apk" }
}

val copyFossWearApk = tasks.register<Copy>("copyFossWearApk") {
    val wearProject = project(":wear")
    dependsOn("${wearProject.path}:assembleFossRelease")
    from(wearProject.layout.buildDirectory.dir("outputs/apk/foss/release"))
    include { it.name.endsWith(".apk") && !it.name.contains("-unsigned") }
    into(layout.projectDirectory.dir("src/foss/assets"))
    rename { "wear_companion.apk" }
}

android {
    namespace = "com.steel101.wearsyncforbreezy"
    compileSdk = 37

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.steel101.wearsyncforbreezy"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.0.49"
        resConfigs("en")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val isCi = System.getenv("GITHUB_ACTIONS") == "true"
            val storePath = project.findProperty("ANDROID_KEYSTORE_FILE")?.toString()
                ?: System.getenv("ANDROID_KEYSTORE_FILE")
                ?: localProperties.getProperty("ANDROID_KEYSTORE_FILE")
                ?: if (isCi) "" else "C:/Users/steel/github"

            if (storePath.isNotEmpty()) {
                val storeFilePath = file(storePath)
                if (storeFilePath.exists()) {
                    storeFile = storeFilePath
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
        release {
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile?.exists() == true) {
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

    project.afterEvaluate {
        tasks.named("mergeGooglePlayReleaseAssets") { dependsOn(copyGooglePlayWearApk) }
        tasks.named("mergeGooglePlayDebugAssets") { dependsOn(copyGooglePlayWearApk) }
        tasks.named("mergeFossReleaseAssets") { dependsOn(copyFossWearApk) }
        tasks.named("mergeFossDebugAssets") { dependsOn(copyFossWearApk) }
        
        tasks.matching { it.name.contains("lint", ignoreCase = true) }.configureEach {
            if (name.contains("GooglePlay", ignoreCase = true)) {
                dependsOn(copyGooglePlayWearApk)
            } else if (name.contains("Foss", ignoreCase = true)) {
                dependsOn(copyFossWearApk)
            }
        }
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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.startup)

    "googlePlayImplementation"(libs.play.services.wearable)
    "googlePlayImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    "fossImplementation"(libs.hivemq.mqtt.client)

    implementation(project(":shared"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
