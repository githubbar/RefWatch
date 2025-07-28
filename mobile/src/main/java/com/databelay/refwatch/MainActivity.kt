package com.databelay.refwatch

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
import com.databelay.refwatch.common.theme.RefWatchMobileTheme
import com.databelay.refwatch.navigation.RefWatchNavHost
import dagger.hilt.android.AndroidEntryPoint

// No need for androidx.hilt.navigation.compose.hiltViewModel here
//TODO: release test into playstore https://play.google.com/console/u/0/developers/7846019045633488606/app-list
// FIXME: if phone app is not started, watch starts without games; is it a debug artifact?
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
