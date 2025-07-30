package com.databelay.refwatch.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.navigation.NavigationRoutes
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint // This annotation enables Hilt injection for the Activity
class MainActivity : ComponentActivity() {
    // The Activity's only job is to set up the Compose content.
    // Data reception is handled by your background WearableListenerService.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RefWatchWearTheme {
                // Call your main Composable that contains the navigation logic.
                NavigationRoutes()
            }
        }
    }
}