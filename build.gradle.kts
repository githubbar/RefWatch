// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false

    id("com.google.dagger.hilt.android") version "2.56.2" apply false // Add this for Hilt
    id("com.google.devtools.ksp") version "2.1.21-2.0.1" apply false // Add this for KSP (if using Room with KSP, etc.)
    id("com.google.gms.google-services") version "4.4.2" apply false
}
//kotlin = 2.0.21

//tasks.register("clean", Delete::class) {
//    delete(rootProject.buildDir)
//}