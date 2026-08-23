import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.screenshot)
    kotlin("plugin.serialization") version "2.1.21"
    id("com.google.gms.google-services") // If your wear app uses Firebase directly
    id("com.google.devtools.ksp")        // Apply KSP if you use it for Room, etc.
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.databelay.refwatch"
    compileSdk = 36
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    defaultConfig {
        applicationId = "com.databelay.refwatch"
        minSdk = 34
        targetSdk = 36
//        Version code scheme explained here:  https://developer.android.com/training/wearables/packaging
        versionCode = 361150001
        versionName = "1.1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"$buildTime\""
        ) // BUILD_TIME becomes accessible in code
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    useLibrary("wear-sdk")
}

dependencies {
    implementation(project(":common"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.wear.compose.navigation) // If you're using Wear Navigation
    implementation(libs.androidx.navigation.runtime.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)


    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.play.services.auth) // Check for latest
    implementation(libs.hilt.android) // Use the same version as the plugin
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.gson)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.horologist.compose.layout)
    implementation(libs.horologist.compose.material)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.health.services)
    implementation(libs.androidx.compose.material3)
    
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.foundation)

    screenshotTestImplementation(libs.kotlinx.coroutines.android)
    screenshotTestImplementation(libs.kotlinx.coroutines.core)
    screenshotTestImplementation(libs.screenshot.validation.api)

    ksp(libs.hilt.compiler)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.wear.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

