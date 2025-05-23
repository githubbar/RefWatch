plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
//    id("kotlin-parcelize")
    kotlin("plugin.serialization") version "2.1.21"
//    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android") // Apply the Hilt plugin here
    id("com.google.devtools.ksp")        // Apply KSP if you use it for Room, etc.
}

android {
    namespace = "com.databelay.refwatch.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.databelay.refwatch.wear"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
//        freeCompilerArgs += "-Xlint:deprecation"
    }
    buildFeatures {
        compose = true
    }
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
    implementation(libs.androidx.activity.compose.v182)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.navigation.v130) // If you're using Wear Navigation
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.v141)
    implementation(libs.androidx.compose.foundation.v141)
    implementation(libs.androidx.compose.navigation.v130)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth) // Check for latest
    implementation(libs.hilt.android) // Use the same version as the plugin
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.runtime.android)
    implementation(libs.kotlinx.serialization.json) // For deserialization
    implementation(libs.androidx.lifecycle.viewmodel.compose) // For ViewModels
    implementation(libs.play.services.wearable)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0") // Crucial
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0") // For coroutines

    ksp(libs.hilt.compiler)

    debugImplementation(libs.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    }