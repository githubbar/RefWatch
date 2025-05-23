plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.1.21"
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android") // Apply the Hilt plugin here
    id("com.google.devtools.ksp")        // Apply KSP if you use it for Room, etc.
}

android {
    namespace = "com.databelay.refwatch.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.databelay.refwatch.mobile"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose.v182)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android) // You likely have this or core
    implementation(libs.kotlinx.coroutines.play.services) // Or the latest version
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.navigation.v130) // If you're using Wear Navigation
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.v141)
    implementation(libs.androidx.compose.foundation.v141)
    implementation(libs.androidx.compose.navigation.v130)
    implementation(libs.kotlinx.serialization.json)


    // ---------- Begin Firebase Setup -----------------
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    // When using the BoM, you don't specify versions in Firebase library dependencies
    // Add the dependency for the Firebase SDK for Google Analytics
    implementation("com.google.firebase:firebase-analytics")
    // See https://firebase.google.com/docs/android/setup#available-libraries
    // For example, add the dependencies for Firebase Authentication and Cloud Firestore
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // ---------- End Firebase Setup -----------------


    implementation(libs.play.services.auth) // Check for latest
    implementation(libs.play.services.wearable) // Or latest version
    implementation(libs.hilt.android) // Use the same version as the plugin
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.runtime.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.play.services) // Or the latest version
    implementation(libs.kotlinx.coroutines.android) // Or latest, often same as play-services one


    testImplementation(libs.junit)
    testImplementation(libs.jetbrains.kotlin.test.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core.v351)
    androidTestImplementation(libs.truth) // For assertions
    androidTestImplementation(libs.kotlinx.coroutines.test) // For testing coroutines
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)

 }