package com.databelay.refwatch.wear

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.navigation.NavigationRoutes
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint // This annotation enables Hilt injection for the Activity
class MainActivity : ComponentActivity() {
    // The Activity's only job is to set up the Compose content.
    // Data reception is handled by your background WearableListenerService.
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RefWatchWearTheme {
                // Call your main Composable that contains the navigation logic.
                NavigationRoutes()
            }
        }
    }

/*
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called - Activity has focus")
        // If you were using FLAG_KEEP_SCREEN_ON:
        // window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        Log.w(TAG, "onPause called - Activity losing focus") // Use Warn log level to make it stand out
        // If you were using FLAG_KEEP_SCREEN_ON:
        // window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        Log.w(TAG, "onStop called - Activity no longer visible")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "onDestroy called - Activity is being destroyed")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            Log.d(TAG, "onWindowFocusChanged: Window has gained focus")
        } else {
            // This is a key place to log when focus is lost!
            Log.w(TAG, "onWindowFocusChanged: Window has lost focus")
        }
    }*/


}