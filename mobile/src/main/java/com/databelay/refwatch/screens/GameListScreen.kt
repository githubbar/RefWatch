package com.databelay.refwatch.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.auth.AuthState
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.data.ExplanationArea
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Locale

// Data class to define the structure and behavior of context menu items
data class ContextMenuItemAction(
    val label: String,
    val action: (game: Game) -> Unit // The action lambda now takes the specific Game as a parameter
)

val tag = "GameListScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    authStateValue: com.databelay.refwatch.auth.AuthState, // << Pass AuthState directly
    games: kotlin.collections.List<com.databelay.refwatch.common.Game>, // << Pass the list of games to display directly
    selectedTab: com.databelay.refwatch.common.GameStatus,    // << Pass current tab
    scrollToTopSignal: kotlinx.coroutines.flow.SharedFlow<kotlin.Unit>?, // Event stream passed in
    onTabSelected: (com.databelay.refwatch.common.GameStatus) -> kotlin.Unit, // << Callback to change tab
    onAddGame: () -> kotlin.Unit,
    onEditGame: (com.databelay.refwatch.common.Game) -> kotlin.Unit,
    onViewLog: (com.databelay.refwatch.common.Game) -> kotlin.Unit, // <-- Ensure this is passed
    onDeleteGame: (com.databelay.refwatch.common.Game) -> kotlin.Unit,
    onSignOut: () -> kotlin.Unit, // This might be handled by the calling composable via AuthViewModel
    onImportGames: () -> kotlin.Unit, // Callback for importing
    onNavigateToSettings: () -> kotlin.Unit,
    onboardingStep: com.databelay.refwatch.data.OnboardingStep?, // << Pass the list of onboarding steps,
    onNextTooltip: () -> kotlin.Unit,
    onDismissTooltip: () -> kotlin.Unit
) {
    /*    // ================================== BEGIN ULTIMATE DEBUG:
        // This is the most minimal test possible.
        Scaffold(
            topBar = { TopAppBar(title = { Text("Ultimate TooltipBox Test") }) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                val tooltipState = rememberTooltipState(isPersistent = true, initialIsVisible = true)
                val scope = rememberCoroutineScope()
                // This LaunchedEffect will trigger whenever the onboardingStep changes.
                // Use the onboardingStep itself as the key. When it recomposes with a new step object,
                // this effect will relaunch, which is what we want.
                LaunchedEffect(onboardingStep) {
                    // If there is a current, valid step, show its tooltip.
                    // If the step is null (tour is inactive/finished), this does nothing.
                    onboardingStep?.let { step ->
                        Log.d(tag, "LaunchedEffect for step: ${step.title}. Requesting show().")
                        // The show() function is suspend, so the coroutine will pause here.
                        // If a recomposition happens while it's paused, this coroutine will be
                        // cancelled and a new one will start.
                        tooltipState.show()
                        // This log will likely not be seen if a recomposition happens quickly,
                        // which we've established is the case. This is expected behavior.
                        Log.d(tag, "show() has completed for step: ${step.title}.")
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(8.dp),
                    tooltip = {
                        RichTooltip(
                            title = { Text("Title of the tooltip") },
                            action = {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            tooltipState.dismiss()
                                        }
                                    }
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        ) {
                            Text("This is the main content of the rich tooltip")
                        }
                    },
                    state = tooltipState
                ) {
                    Button(onClick = {
                        scope.launch {
                            tooltipState.show()
                        }
                    }) {
                        Text(text = "Show Rich Tooltip on Click")
                    }
                }
            }
        }
        // ==================================  END DEBUG*/
    Log.d(
        tag,
        "Received games: ${games.map { it.id + " -> " + it.status }}"
    ) // Log input games

    // Filter and sort the lists, just like on the watch
    val (upcomingGames, pastGames) = remember(games) {
        val (scheduled, completed) = games.partition { it.status == GameStatus.SCHEDULED }
        Pair(
            scheduled.sortedBy { it.gameDateTimeEpochMillis },
            completed.sortedByDescending { it.gameDateTimeEpochMillis }
        )
    }
    val gamesToDisplay = if (selectedTab == GameStatus.SCHEDULED) upcomingGames else pastGames
    val lazyListState = rememberLazyListState()

    // --- Collect UI events ---
    LaunchedEffect(key1 = scrollToTopSignal) { // Key on the signal itself in case it could change
        scrollToTopSignal?.collectLatest {
            Log.d(tag, "ScrollToTop event received in UI.")
            if (gamesToDisplay.isNotEmpty()) {
                lazyListState.animateScrollToItem(index = 0)
            }
        }
    }



    Scaffold(

        topBar = {
            TopAppBar(
                title = { Text("My Games") },
                actions = {
                    // Conditionally display Settings Button based on AuthState
                    if (authStateValue is AuthState.Authenticated) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                    ExplanationArea(
                        tag = "import_ics_button", // <-- The tag we defined
                        onboardingStep = onboardingStep,
                        onNext = onNextTooltip,
                        onDismiss = onDismissTooltip
                    ) {
                        IconButton(onClick = onImportGames) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Import ICS")
                        }
                    }
                    ExplanationArea(
                        tag = "sign_out_button", // <-- The tag we defined
                        onboardingStep = onboardingStep,
                        onNext = onNextTooltip,
                        onDismiss = onDismissTooltip
                    ) {
                        IconButton(onClick = onSignOut) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign Out"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExplanationArea(
                tag = "add_game_fab",
                onboardingStep = onboardingStep,
                onNext = onNextTooltip,
                onDismiss = onDismissTooltip
            ) {
                FloatingActionButton(onClick = onAddGame) {
                    Icon(Icons.Filled.Add, "Add Game")
                }
            }
        }
    ) { paddingValues ->
        // This LaunchedEffect will trigger whenever the onboardingStep changes.
        // Use the onboardingStep itself as the key. When it recomposes with a new step object,
        // this effect will relaunch, which is what we want.
        val tooltipState = rememberTooltipState(isPersistent = true, initialIsVisible = true)

        LaunchedEffect(onboardingStep) {
            // If there is a current, valid step, show its tooltip.
            // If the step is null (tour is inactive/finished), this does nothing.
            onboardingStep?.let { step ->
                Log.d(tag, "LaunchedEffect for step: ${step.title}. Requesting show().")
                // The show() function is suspend, so the coroutine will pause here.
                // If a recomposition happens while it's paused, this coroutine will be
                // cancelled and a new one will start.
                step.tooltipState.show()
//                tooltipState.show()
                // This log will likely not be seen if a recomposition happens quickly,
                // which we've established is the case. This is expected behavior.
                Log.d(tag, "show() has completed for step: ${step.title}.")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            // --- TABS FOR FILTERING ---
            // ScrollableTabRow sizes each tab to its label instead of splitting the width
            // evenly, so "Upcoming (12)" is not truncated at large font scales.
            PrimaryScrollableTabRow(
                selectedTabIndex = if (selectedTab == GameStatus.SCHEDULED) 0 else 1,
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = selectedTab == GameStatus.SCHEDULED,
                    onClick = { onTabSelected(GameStatus.SCHEDULED) },
                    text = { Text("Upcoming (${upcomingGames.size})") }
                )
                Tab(
                    selected = selectedTab == GameStatus.COMPLETED,
                    onClick = { onTabSelected(GameStatus.COMPLETED) },
                    text = { Text("Past (${pastGames.size})") }
                )
            }
            // --- GAME LIST or EMPTY MESSAGE ---
            if (gamesToDisplay.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No games scheduled. Add one or import ICS.")
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp)
                ) {
                    items(gamesToDisplay, key = { it.id }) { game ->
                        GameListItem(
                            game = game,
                            onEditGame = { onEditGame(game) },
                            onViewLog = onViewLog, // <-- Pass it down to the item
                            onDeleteGame = { onDeleteGame(game) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GameListItem(
    game: Game,
    onEditGame: (Game) -> Unit,
    onViewLog: (Game) -> Unit,
    onDeleteGame: (Game) -> Unit
) {
    val dateFormat =
        remember { SimpleDateFormat("EEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }
    val context = LocalContext.current // Get context for launching Intent
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    onClick = {
                        showContextMenu = false // Dismiss context menu on click
                        if (game.status == GameStatus.SCHEDULED) {
                            onEditGame(game)
                        } else {
                            onViewLog(game)
                        }
                    },
                    onLongClick = {
                        showContextMenu = true
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f) // <<<< KEY CHANGE: Makes this column flexible
                        .padding(end = 8.dp) // Optional: Add some padding between text and button
                ) {
                    Text(
                        "${game.homeTeamName} vs ${game.awayTeamName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "#${game.gameNumber}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.surfaceTint
                    )
                    game.ageGroup?.let {
                        Text(
                            "Age Group: ${it.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    game.formattedGameDateTime?.let {
                        Text("Time: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    // --- Display Venue and/or Field Number ---
                    val locationDetails = mutableListOf<String>()
                    game.venue?.takeIf { it.isNotBlank() }?.let { venueText ->
                        locationDetails.add(venueText) // Add venue if it exists
                    }
                    game.fieldNumber?.takeIf { it.isNotBlank() }?.let { fieldNum ->
                        locationDetails.add("Field: $fieldNum") // Add field number if it exists
                    }

                    if (locationDetails.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = locationDetails.joinToString(" - "), // e.g., "City Park - Field: 3" or just "Field: 3" or just "City Park"
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                        }
                    }
                    // --- End Display Venue and/or Field Number ---
                    Text(
                        "H: ${game.homeScore} - A: ${game.awayScore}",
                        /*($ { game.currentPhase.readable() })*/
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Right side: Location IconButton (if location exists) and Delete IconButton
                Column(horizontalAlignment = Alignment.End) { // Aligns buttons to the right
                    val fullLocationQuery = remember(game.venue, game.fieldNumber) {
                        val venuePart = game.venue?.takeIf { it.isNotBlank() } ?: ""
                        val fieldPart =
                            game.fieldNumber?.takeIf { it.isNotBlank() }?.let { "Field $it" } ?: ""

                        if (venuePart.isNotBlank() && fieldPart.isNotBlank() && venuePart.contains(
                                fieldPart,
                                ignoreCase = true
                            )
                        ) {
                            venuePart
                        } else if (venuePart.isNotBlank() && fieldPart.isNotBlank()) {
                            "$venuePart, $fieldPart"
                        } else {
                            venuePart.ifBlank { fieldPart }
                        }
                    }
                    val iconButtonHeight = 40.dp

                    if (fullLocationQuery.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val mapQuery = Uri.encode(fullLocationQuery)
                                val gmmIntentUri = Uri.parse("geo:0,0?q=$mapQuery")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                if (mapIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(mapIntent)
                                } else {
                                    val genericMapIntent =
                                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$mapQuery"))
                                    try {
                                        context.startActivity(genericMapIntent)
                                    } catch (e: Exception) {
                                        Log.e(
                                            "MapLink",
                                            "No map app found to handle: $fullLocationQuery",
                                            e
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .height(iconButtonHeight)
                                .size(iconButtonHeight)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "Open in Maps",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        Box(modifier = Modifier.height(iconButtonHeight))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    IconButton(
                        onClick = { onDeleteGame(game) }, // This IconButton is now outside the context menu logic
                        modifier = Modifier
                            .height(iconButtonHeight)
                            .size(iconButtonHeight)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            "Delete Game",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // 1. Define the list of actions for the context menu.
        //    This list is remembered. The `action` lambdas here just define WHAT to do.
        val contextMenuActions =
            remember(game.status) { // Re-calculate if available actions change based on game status
                Log.d(
                    "GameListItem",
                    "Defining context menu actions for game: ${game.id}, status: ${game.status}"
                )
                listOfNotNull(
                    ContextMenuItemAction("Edit Game") { gameParam ->
                        Log.d(
                            "GameListItem",
                            "Action 'Edit Game' invoked for game: ${gameParam.id}"
                        )
                        onEditGame(gameParam)
                    },
                    // Conditionally add "View Log" if applicable
                    ContextMenuItemAction("View Log") { gameParam ->
                        Log.d("GameListItem", "Action 'View Log' invoked for game: ${gameParam.id}")
                        onViewLog(gameParam)
                    },
                    ContextMenuItemAction("Delete Game") { gameParam -> //
                        Log.d("GameListItem", "Action 'Delete Game' invoked")
                        onDeleteGame(gameParam)
                    }
                )
            }

        // 2. Build the DropdownMenu using these actions.
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            contextMenuActions.forEach { itemDefinition ->
                DropdownMenuItem(
                    text = { Text(itemDefinition.label) },
                    // 3. The onClick for the DropdownMenuItem itself.
                    //    This lambda captures the CURRENT 'game' from GameListItem's scope.
                    onClick = {
                        Log.d(
                            "GameListItem",
                            "Context Menu Item '${itemDefinition.label}' CLICKED. Game to use: ${game.id} (${game.homeTeamName})"
                        )
                        itemDefinition.action(game) // Pass the CURRENT 'game' to the defined action
                        showContextMenu = false
                    }
                    // You could add leadingIcon here if ContextMenuItemAction had an icon property
                )
            }
        }
    }
}


/*
// -------------------------------- PREVIEWS ----------------------------------------------------
// ----------------------------------------------------------------------------------------------
// Helper to create sample games for previews
@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    device = "id:medium_phone",
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(
    device = "id:small_phone",
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun GameListScreenPreview_Unauthenticated() {
    RefWatchMobileTheme {
        GameListScreen(
            authStateValue = AuthState.Unauthenticated,
            games = createSampleGames(),
            selectedTab = GameStatus.SCHEDULED, // Default, not really visible
            scrollToTopSignal = null,
            onTabSelected = {},
            onAddGame = {},
            onEditGame = {},
            onViewLog = {},
            onDeleteGame = {},
            onSignOut = {},
            onImportGames = {},
            onNavigateToSettings = {},
            onboardingStep = OnboardingStep.defaults(),
            onNextTooltip = {},
            onDismissTooltip = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    device = "id:pixel_9",
    showSystemUi = true,
    showBackground = true,
    name = "GameList - Loading",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun GameListScreenPreview_Loading() {
    RefWatchMobileTheme {
        GameListScreen(
            authStateValue = AuthState.Unauthenticated,
            games = createSampleGames(),
            selectedTab = GameStatus.COMPLETED, // Default
            scrollToTopSignal = null,
            onTabSelected = {},
            onAddGame = {},
            onEditGame = {},
            onViewLog = {},
            onDeleteGame = {},
            onSignOut = {},
            onImportGames = {},
            onNavigateToSettings = {},
            onboardingStep = OnboardingStep.defaults(),
            onNextTooltip = {},
            onDismissTooltip = {}
        )
    }
}
*/


