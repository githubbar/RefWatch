package com.databelay.refwatch.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.navigation.NavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object LegalLinks { // Using an object to group them
    const val PRIVACY_POLICY_URL = "https://doc-hosting.flycricket.io/refwatch-privacy-policy/3571da7e-481d-4199-adfb-921382bad8be/privacy"
    const val TERMS_OF_USE_URL = "https://doc-hosting.flycricket.io/refwatch-terms-of-use/34d1063e-7d93-40d5-8016-5ede5ab4c1c1/terms"
}

/**
 * Checks if Google Play Services are available and if the Google Maps API key is configured.
 */
fun isGoogleMapsAvailable(context: Context): Boolean {
    val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    if (availability != ConnectionResult.SUCCESS) return false

    // Check if API key is present in manifest
    return try {
        val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val bundle = ai.metaData
        val apiKey = bundle.getString("com.google.android.geo.API_KEY")
        // Basic check: not null, not empty, and not the placeholder
        !apiKey.isNullOrEmpty() && apiKey != "YOUR_API_KEY"
    } catch (e: Exception) {
        false
    }
}

// Function to get the application's version name
fun getAppVersionName(context: Context): String {
    return try {
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "N/A" // Return "N/A" or some default if null
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        "N/A" // Or handle the exception as appropriate
    }
}

// Function to get the application's version code
fun getAppVersionCode(context: Context): Long { // Or Int if you don't expect very large version codes
    return try {
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        -1L // Or handle the exception as appropriate
    }
}


@SuppressLint("RestrictedApi")
fun logBackStack(navController: NavController, contextMessage: String = "") {
    val stack = navController.currentBackStack.value
    val currentNavControllerDestination = navController.currentDestination
    val currentNavControllerRoute = currentNavControllerDestination?.route
    val currentNavControllerId = currentNavControllerDestination?.id
    val currentNavControllerClass =
        currentNavControllerDestination?.displayName

    val TAG = "LogBackStack"
    Log.d("${TAG}:stack", "---- NavController Back Stack ($contextMessage) ----")
    Log.d(
        "${TAG}:stack",
        "NavController Current Destination: Route='${currentNavControllerRoute ?: "null"}', ID='${currentNavControllerId ?: "null"}', Class='${currentNavControllerClass ?: "null"}'"
    )

    if (stack.isEmpty()) {
        Log.d("${TAG}:stack", "Back stack is empty.")
    } else {
        stack.forEachIndexed { index, navBackStackEntry ->
            val entryDestination = navBackStackEntry.destination
            val route = entryDestination.route
            val arguments = navBackStackEntry.arguments?.toString() ?: "null"
            val destDisplayName = entryDestination.displayName

            Log.d(
                "${TAG}:stack",
                "$index: Route='${route ?: "null"}', Args=[$arguments], ID='${navBackStackEntry.id}', NavDestId='${entryDestination.id}', NavDestClass='${destDisplayName}'"
            )
        }
    }
    Log.d("${TAG}:stack", "------------------------------------------")
}
