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

// Version code scheme: 4 | major | minor(2) | patch(2) | variant(2)
//   401020000 = v1.2.0, mobile
//
// The 400_000_000 floor exists so every derived code stays above 361190001, the last
// code published under the previous scheme. That scheme was
//   36 * 10_000_000 + (major*100 + minor*10 + patch) * 10_000 + variant
// which gave minor and patch a single digit each, so 1.1.10 and 1.2.0 both produced
// 361200000. Play requires version codes to be unique and strictly increasing, so that
// collision would have made one of those versions unpublishable. Minor and patch now
// get two digits each; the ceiling is 9.99.99 -> 409999900, well under Play's
// 2100000000 limit.
fun refWatchVersionCode(versionName: String, variant: Int): Int {
    val parts = versionName.substringBefore('-').split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return 400_000_000 + major * 1_000_000 + minor * 10_000 + patch * 100 + variant
}

// Local builds use the fallback; CI passes -PversionName=<tag without the "v">,
// so tagging v1.2.0 yields versionName 1.2.0 and versionCode 401020000.
// -PversionCode=<int> overrides the derived code when you need to hand-pick it.
val appVersionName: String = (findProperty("versionName") as String?)?.removePrefix("v") ?: "1.2.0"
val appVersionCode: Int = (findProperty("versionCode") as String?)?.toInt()
    ?: refWatchVersionCode(appVersionName, variant = 0)

// Optional local release signing. See the matching comment in wear/build.gradle.kts:
// CI leaves these properties unset and signs its own output, so this only affects
// local release builds.
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
        versionCode = appVersionCode
        versionName = appVersionName
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