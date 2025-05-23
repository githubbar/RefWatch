package com.databelay.refwatch.mobile // Your phone app's package name

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.databelay.refwatch.common.theme.RefWatchMobileTheme // Your theme
import com.databelay.refwatch.mobile.navigation.RefWatchNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint // Ensures Hilt can inject into this Activity if needed (though usually not for ViewModels directly here)
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
}

