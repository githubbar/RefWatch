package com.databelay.refwatch.screens

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.databelay.refwatch.BuildConfig
import com.databelay.refwatch.common.LegalLinks
import com.databelay.refwatch.common.getAppVersionCode
import com.databelay.refwatch.common.getAppVersionName
import com.databelay.refwatch.common.theme.RefWatchMobileTheme

// Assuming you have an AuthViewModel or similar to handle account deletion
// import com.databelay.refwatch.auth.AuthViewModel
// import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import com.databelay.refwatch.data.SettingsViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onDeleteAccountConfirmed: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var appVersionName by remember { mutableStateOf("Loading...") }
    var appVersionNumber by remember { mutableLongStateOf(0L) }
    val buildDateString = BuildConfig.BUILD_TIME

    val collectPositionInfo by settingsViewModel.collectPositionInfo.collectAsStateWithLifecycle()

    // LaunchedEffect to get version name (it's a synchronous call but good practice
    // if it were asynchronous, and keeps UI responsive during initial composition)
    LaunchedEffect(Unit) {
        appVersionName = getAppVersionName(context)
        appVersionNumber = getAppVersionCode(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                "Legal",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Privacy Policy Link
            val privacyPolicyAnnotatedString = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Privacy Policy")
                }
            }

            Text(
                text = privacyPolicyAnnotatedString,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.PRIVACY_POLICY_URL))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Failed to open Privacy Policy URL: ${LegalLinks.PRIVACY_POLICY_URL}", e)
                        }
                    }
            )

            // Terms of Use Link
            val termsOfUseAnnotatedString = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Terms of Use")
                }
            }

            Text(
                text = termsOfUseAnnotatedString,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.TERMS_OF_USE_URL))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Failed to open Terms of Use URL: ${LegalLinks.TERMS_OF_USE_URL}", e)
                        }
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Preferences",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Collect Position Info",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Enable GPS tracking during games to see movement maps and analytics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = collectPositionInfo,
                    onCheckedChange = { settingsViewModel.setCollectPositionInfo(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp)) // More space before other settings

            // --- ADD BUILD INFO TEXT HERE ---
            Text(
                text = "Version: $appVersionName $buildDateString", // Display version name
                color = androidx.wear.compose.material.MaterialTheme.colors.primary,
                style = androidx.wear.compose.material.MaterialTheme.typography.caption1.copy(fontSize = 14.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            // --- END BUILD INFO TEXT ---
            Spacer(modifier = Modifier.weight(1f)) // Pushes delete account to bottom

            Button(
                onClick = { showDeleteConfirmationDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Account")
            }
        }
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("Delete Account?") },
            text = { Text("This action is permanent and cannot be undone. All your data will be erased. Are you sure you want to delete your account?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        // Trigger the actual account deletion logic
                        // authViewModel.deleteUserAccount() // Example call to ViewModel
                        onDeleteAccountConfirmed()
                        Log.d("SettingsScreen", "Account deletion confirmed by user.")
                        // After deletion, you'll likely navigate the user away (e.g., to login screen)
                        // This navigation should be handled after the onDeleteAccountConfirmed callback completes,
                        // potentially observing a state from the ViewModel.
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmationDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun SettingsScreenMobilePreview() {
    RefWatchMobileTheme {
        SettingsScreen(onNavigateBack = {}, onDeleteAccountConfirmed = {})
    }
}
