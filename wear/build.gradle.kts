import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.1.21"
    id("com.google.devtools.ksp")        // Apply KSP if you use it for Room, etc.
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.databelay.refwatch"
    compileSdk = 36

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.databelay.refwatch"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "Peppercorn" // The versions SHALL all be spherical objects of increasing size
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"") // BUILD_TIME becomes accessible in code
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    useLibrary("wear-sdk")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.compose.navigation)
    implementation(libs.kotlinx.coroutines.android) // You likely have this or core
    implementation(libs.kotlinx.coroutines.play.services) // Or the latest version
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.navigation) // If you're using Wear Navigation
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth) // Check for latest
    implementation(libs.hilt.android) // Use the same version as the plugin
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.runtime.android)
    implementation(libs.kotlinx.serialization.json) // For deserialization
    implementation(libs.androidx.lifecycle.viewmodel.compose) // For ViewModels
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.wearable) // Crucial
    implementation(libs.androidx.lifecycle.runtime.ktx) // For coroutines
    implementation(libs.gson)
    implementation(libs.androidx.compose.ui.ui.tooling)
    implementation(libs.androidx.wear.ongoing)

    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.ui.tooling) // Or latest version
    debugImplementation(libs.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}