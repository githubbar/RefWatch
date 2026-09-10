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

// Version code scheme: 4 | major | minor(2) | patch(2) | variant(2)
//   401020001 = v1.2.0, wear
//
// The 400_000_000 floor exists so every derived code stays above 361190001, the last
// code published under the previous scheme. That scheme was
//   36 * 10_000_000 + (major*100 + minor*10 + patch) * 10_000 + variant
// which gave minor and patch a single digit each, so 1.1.10 and 1.2.0 both produced
// 361200001. Play requires version codes to be unique and strictly increasing, so that
// collision would have made one of those versions unpublishable. Minor and patch now
// get two digits each; the ceiling is 9.99.99 -> 409999901, well under Play's
// 2100000000 limit.
fun refWatchVersionCode(versionName: String, variant: Int): Int {
    val parts = versionName.substringBefore('-').split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return 400_000_000 + major * 1_000_000 + minor * 10_000 + patch * 100 + variant
}

// Local builds use the fallback; CI passes -PversionName=<tag without the "v">,
// so tagging v1.2.0 yields versionName 1.2.0 and versionCode 401020001.
// -PversionCode=<int> overrides the derived code when you need to hand-pick it.
val appVersionName: String = (findProperty("versionName") as String?)?.removePrefix("v") ?: "1.2.0"
val appVersionCode: Int = (findProperty("versionCode") as String?)?.toInt()
    ?: refWatchVersionCode(appVersionName, variant = 1)

// Optional local release signing.
//
// CI does NOT use this: it builds unsigned and then signs with apksigner/jarsigner from
// the ANDROID_KEYSTORE_* repository secrets. These properties are absent on CI, so
// canSignLocally is false there and the workflow's own signing step still applies.
//
// The Play upload key is the PKCS#12 keystore Android Studio has been using, alias
// `key0` (SHA1 A2:91:2C:C8:...). NOTE it is a different key from
// ~/keystores/refwatch-release.jks, which was generated later for CI and which Play
// rejects -- see the Signing section of CLAUDE.md before changing any of this.
//
// To sign locally, add these to ~/.gradle/gradle.properties -- never to a file inside
// this repository:
//   refwatchStoreFile=C:/Users/oleyk/keys_for_android_studio
//   refwatchStorePassword=<store password>
//   refwatchKeyAlias=key0
//   refwatchKeyPassword=<key password, same as the store password for PKCS#12>
val releaseStoreFile = (findProperty("refwatchStoreFile") as String?)?.let { file(it) }
val releaseStorePassword = findProperty("refwatchStorePassword") as String?
val releaseKeyAlias = findProperty("refwatchKeyAlias") as String?
val releaseKeyPassword = findProperty("refwatchKeyPassword") as String?
val canSignLocally = releaseStoreFile?.exists() == true &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.databelay.refwatch"
    compileSdk = 36
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    signingConfigs {
        if (canSignLocally) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

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
        versionCode = appVersionCode
        versionName = appVersionName
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
            // null on CI, where the workflow signs the output itself.
            signingConfig = if (canSignLocally) signingConfigs.getByName("release") else null
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
    implementation(libs.androidx.compose.foundation)
    implementation(libs.play.services.auth) // Check for latest
    implementation(libs.hilt.android) // Use the same version as the plugin
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.gson)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.horologist.compose.layout)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.wear.input)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.health.services)
    
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

