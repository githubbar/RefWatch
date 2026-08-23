import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.1.21"
    id("com.google.gms.google-services") // If your mobile app uses Firebase directly
    id("com.google.devtools.ksp")        // Apply KSP if you use it for Room, etc.
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.databelay.refwatch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.databelay.refwatch"
        minSdk = 31
        targetSdk = 36
//        Version code scheme explained here:  https://developer.android.com/training/wearables/packaging
//        Here is a suggested version code scheme:
//
//        Set the first two digits of the version code to the targetSdkVersion, such as 28.
//        Set the next three digits to the product version, such as 152 for a product version of 1.5.2.
//        Set the next two digits to the build or release number, such as 01.
//        Reserve the last two digits for a multi-APK variant, such as 00.
//
//        For example, the sample values here—28, 152, 01, and 00—result in a version code of 281520100.
        versionCode = 361150000
        versionName = "1.1.5"
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"") // BUILD_TIME becomes accessible in code
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
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
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.play.services.auth) // Check for latest
    implementation(libs.play.services.wearable)
    implementation(libs.material)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.foundation)

    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.compose.ui)
    implementation(libs.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android) // You likely have this or core
    implementation(libs.kotlinx.coroutines.play.services) // Or the latest version
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.gson) // Or latest version
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)
    implementation(libs.google.maps.utils)
    implementation(libs.play.services.maps)


    debugImplementation(libs.mockito.core)
    testImplementation(libs.junit)
    testImplementation(libs.google.truth) // Or a newer version
    // For Android Instrumented tests (like yours in androidTest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    ksp(libs.hilt.compiler) // Or kapt
}