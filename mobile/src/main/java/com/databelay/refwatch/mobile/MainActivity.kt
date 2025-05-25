package com.databelay.refwatch.mobile // Your phone app's package name

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.theme.RefWatchMobileTheme // Your theme
import com.databelay.refwatch.mobile.navigation.RefWatchNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue

// TEMP: commented out to test the minial config
/*@AndroidEntryPoint // Ensures Hilt can inject into this Activity if needed (though usually not for ViewModels directly here)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RefWatchMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // AppContent will now handle its own ViewModel fetching via hiltViewModel()
                    // OR better, use a dedicated NavHost Composable
                    RefWatchNavHost()
                }
            }
        }
    }
}*/

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Get the ViewModel using Hilt's by viewModels() delegate for Activities
    private val viewModel: MainViewModel by viewModels() // <<< CORRECT WAY FOR ACTIVITY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // You can now directly use the 'viewModel' instance obtained above
            MaterialTheme { // Replace with your actual M3 theme if you have one
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { viewModel.onSendPingClicked() },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Send Ping to Watch (Hilt)")
                    }
                }
            }
        }
    }
}