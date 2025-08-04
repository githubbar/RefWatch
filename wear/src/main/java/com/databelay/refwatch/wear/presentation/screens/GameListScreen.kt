package com.databelay.refwatch.wear.presentation.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ChipDefaults.chipColors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.ToggleButton
import androidx.wear.compose.material.ToggleButtonDefaults
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.wear.IWearGameViewModel
import com.databelay.refwatch.wear.data.DataFetchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Assuming GameStatus.SCHEDULED and GameStatus.COMPLETED
enum class GameListFilterState { UPCOMING, PAST }

@Composable
fun CompactGameFilter(
    selectedFilter: GameListFilterState,
    onFilterSelected: (GameListFilterState) -> Unit,
    upcomingCount: Int,
    pastCount: Int,
    modifier: Modifier = Modifier
) {
    val TAG = "GameListScreen"

    // Using Material 3 OutlinedButton for toggle effect, customize as needed
    // For Wear, you might use Chip or Button with custom styling.
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Define filters with their corresponding standard icons
        val filters = listOf(
            GameListFilterState.UPCOMING to Icons.Filled.Event,   // Standard icon for scheduled events
            GameListFilterState.PAST to Icons.Filled.History      // Standard icon for history
        )

        filters.forEach { (filterEnum, iconVector) ->
            val isSelected = selectedFilter == filterEnum
            val contentDescription = when (filterEnum) {
                GameListFilterState.UPCOMING -> "Upcoming games ($upcomingCount)"
                GameListFilterState.PAST -> "Past games ($pastCount)"
            }

            ToggleButton(
                checked = isSelected,
                onCheckedChange = { if (it) onFilterSelected(filterEnum) },
                modifier = Modifier.size(ToggleButtonDefaults.SmallToggleButtonSize),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedBackgroundColor = Color.Green.copy(alpha = .5f),)
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(ToggleButtonDefaults.SmallIconSize)
                )
            }
        }
    }
}
@Composable
fun GameListScreen(
    viewModel: IWearGameViewModel,
    onGameSelected: (Game) -> Unit,
    onViewLog: (String) -> Unit,
    onNavigateToNewGame: () -> Unit,
    onNavigateToGameScreen: () -> Unit
) {
    val allGames by viewModel.gamesList.collectAsState()
    val dataFetchStatus by viewModel.dataFetchStatus.collectAsState()
    val activeGameFromVM by viewModel.activeGame.collectAsState()
    var selectedFilterState by remember { mutableStateOf(GameListFilterState.UPCOMING) }

    val (upcomingGames, pastGames) = remember(allGames) {
        val (scheduled, completed) = allGames.partition { it.status == GameStatus.SCHEDULED }
        Pair(
            scheduled.sortedBy { it.gameDateTimeEpochMillis },
            completed.sortedByDescending { it.gameDateTimeEpochMillis }
        )
    }
    val gamesToDisplay = if (selectedFilterState == GameListFilterState.UPCOMING) upcomingGames else pastGames
    val isGameResumable = remember(activeGameFromVM) {
        activeGameFromVM != null && activeGameFromVM?.status != GameStatus.COMPLETED && activeGameFromVM?.status != GameStatus.SCHEDULED
    }

    LaunchedEffect(Unit) {
        // viewModel.performConnectivityCheck()
    }

    Box(modifier = Modifier.fillMaxSize()) { // Use Box as the root for layering
        // Layer 1: The scrollable list
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(), // List takes the full space of the Box
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
            // Content padding might be needed at the top of the list
            // to prevent content from appearing under the filter bar initially.
            // Adjust this padding based on the actual height of your filter bar.
            // contentPadding = PaddingValues(top = 56.dp) // Example: 48dp for buttons + 8dp padding
        ) {
            // Add a spacer at the top of the list if you don't use contentPadding
            // to prevent the first item from being hidden underneath the filter.
            // The height of this spacer should be roughly the height of your CompactGameFilter.
            item { Spacer(modifier = Modifier.padding(top = 1.dp)) } // Adjust this height

            // "Resume Game" Chip - This will now scroll with the list if placed inside
            // If it should ALSO be fixed, it needs to be a separate layer in the Box too.
            // For simplicity, let's assume it can scroll OR you handle its fixed position separately.

            // "New Ad-Hoc Game" Chip
            if (selectedFilterState == GameListFilterState.UPCOMING) {
                item {
                    Chip(
                        onClick = onNavigateToNewGame,
                        label = { Text("New Game") },
                        icon = { Icon(Icons.Filled.Add, contentDescription = "Start New Ad-Hoc Game") },
                        modifier = Modifier
                            .fillMaxWidth(0.9f), // Adjust padding
                        colors = chipColors(
                            backgroundColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White)
                    )
                }
            }

            if (gamesToDisplay.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyMessage = when {
                            selectedFilterState == GameListFilterState.UPCOMING -> {
                                when (dataFetchStatus) {
                                    DataFetchStatus.INITIAL, DataFetchStatus.ERROR_PHONE_UNREACHABLE -> "Can't fetch games.\nPlease connect to RefWatch on the phone."
                                    DataFetchStatus.FETCHING -> "Loading games..."
                                    DataFetchStatus.ERROR_PARSING -> "Error: Could not read game data from phone."
                                    DataFetchStatus.ERROR_UNKNOWN -> "An error occurred while loading games."
                                    DataFetchStatus.NO_DATA_AVAILABLE -> "No upcoming games scheduled on your phone."
                                    DataFetchStatus.SUCCESS -> "No upcoming games." // Should be NO_DATA_AVAILABLE if list is empty
                                    DataFetchStatus.LOADED_FROM_CACHE -> "No upcoming games in cache. Connect to phone to update." // Or show cached if any
                                }
                            }
                            else -> "No past games." // For "Past" tab
                        }
                        Text(
                            text = emptyMessage,
                            color = MaterialTheme.colorScheme.onSecondary,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(items = gamesToDisplay, key = { game -> game.id }) { game ->
                    ScheduledGameItem(
                        game = game,
                        onClick = {
                            if (game.status == GameStatus.SCHEDULED) {
                                onGameSelected(game)
                            } else {
                                onViewLog(game.id)
                            }
                        }
                    )
                }
            }
        }

        // Layer 2: The CompactGameFilter on top
        // This Column is for potentially stacking other fixed elements like the "Resume Game" chip
        Column(modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)) {
            if (isGameResumable && activeGameFromVM != null) {
                Chip(
                    onClick = onNavigateToGameScreen,
                    label = { Text("Resume Game") },
                    secondaryLabel = { Text("${activeGameFromVM!!.homeTeamName} vs ${activeGameFromVM!!.awayTeamName}") },
                    icon = { Icon(Icons.Outlined.PlayCircle, contentDescription = "Resume Current Game") },
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                            .padding(top = 10.dp, bottom = 4.dp, start = 8.dp, end = 8.dp) // Adjusted padding
                        .align(Alignment.CenterHorizontally),
                    colors = ChipDefaults.primaryChipColors()
                )
            }

            CompactGameFilter(
                selectedFilter = selectedFilterState,
                onFilterSelected = { newFilter -> selectedFilterState = newFilter },
                upcomingCount = upcomingGames.size,
                pastCount = pastGames.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent) // Make the Row background transparent
                    .padding(horizontal = 16.dp, vertical = 4.dp) // Keep its own padding
                // .zIndex(1f) // Usually not needed in a simple Box structure like this
            )
        }
    }
}
@Composable
fun ScheduledGameItem(game: Game, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.95f),
        colors = ChipDefaults.primaryChipColors(),
        label = {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "${game.homeTeamName} vs ${game.awayTeamName}",
                    maxLines = 2, // Allow two lines for long names
                    style = MaterialTheme.typography.labelMedium
                )
                // Show score for completed games
                if (game.status == GameStatus.COMPLETED) {
                    Text(
                        text = "Final: ${game.homeScore} - ${game.awayScore}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        },
        secondaryLabel = {
            Column(horizontalAlignment = Alignment.Start) {
                // Combine venue and date/time if available
                val dateTimeString = game.formattedGameDateTime ?: "No time set"
                val venueString = game.venue?.takeIf { it.isNotBlank() }

                Text(text = dateTimeString)
                if (venueString != null) {
                    Text(text = venueString, maxLines = 1)
                }
            }
        }
    )
}


// ---------------------- PREVIEWS ---------------------------
// -----------------------------------------------------------
class FakeWearGameViewModel (
    initialGames: List<Game> = emptyList(),
    initialFetchStatus: DataFetchStatus = DataFetchStatus.SUCCESS,
    initialActiveGame: Game? = null
) : IWearGameViewModel {
    override val gamesList: StateFlow<List<Game>> = MutableStateFlow(initialGames)
    override val dataFetchStatus: StateFlow<DataFetchStatus> = MutableStateFlow(initialFetchStatus)
    override val activeGame: StateFlow<Game?> = MutableStateFlow(initialActiveGame)

    // Helper to update the list for preview variations
    fun setGames(games: List<Game>) {
        (this.gamesList as MutableStateFlow).value = games
    }
    fun setFetchStatus(status: DataFetchStatus) {
        (this.dataFetchStatus as MutableStateFlow).value = status
    }
    fun setActiveGame(game: Game?) {
        (this.activeGame as MutableStateFlow).value = game
    }
}

// Helper to create sample games for previews
fun createSampleGames(): List<Game> {
    return listOf(
        // --- Scheduled Games ---
        Game.defaults().copy(
            id = "scheduledGame1",
            homeTeamName = "Alpha FC",
            awayTeamName = "Beta United",
            status = GameStatus.SCHEDULED,
            gameDateTimeEpochMillis = System.currentTimeMillis() + (2 * 60 * 60 * 1000L), // 2 hours from now
            venue = "Stadium One",
        ),
        Game.defaults().copy(
            id = "scheduledGame2",
            homeTeamName = "Gamma Rovers",
            awayTeamName = "Delta City",
            status = GameStatus.SCHEDULED,
            homeTeamColorArgb = android.graphics.Color.parseColor("#3F51B5"), // Indigo
            awayTeamColorArgb = android.graphics.Color.parseColor("#FFC107"), // Amber
            gameDateTimeEpochMillis = System.currentTimeMillis() + (26 * 60 * 60 * 1000L), // 26 hours from now
            venue = "Community Park",
        ),

        // --- In-Progress Game Example (for potential "Resume Game" chip) ---
        Game.defaults().copy(
            id = "inProgressGame1",
            homeTeamName = "Red Warriors",
            awayTeamName = "Blue Thunder",
            status = GameStatus.IN_PROGRESS, // Or any active status
            currentPhase = GamePhase.FIRST_HALF,
            isTimerRunning = true,
            displayedTimeMillis = 15 * 60 * 1000L, // 15:00 on the clock
            actualTimeElapsedInPeriodMillis = 15 * 60 * 1000L + (30 * 1000L), // 15m 30s actual
            halfDurationMinutes = 40,
            homeScore = 1,
            awayScore = 0,
            kickOffTeam = Team.HOME,
            homeTeamColorArgb = android.graphics.Color.RED,
            awayTeamColorArgb = android.graphics.Color.BLUE,
        ),

        // --- Completed Games ---
        Game.defaults().copy(
            id = "completedGame1",
            homeTeamName = "Green Hornets",
            awayTeamName = "Purple Haze",
            status = GameStatus.COMPLETED,
            currentPhase = GamePhase.GAME_ENDED,
            isTimerRunning = false,
            gameDateTimeEpochMillis = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L), // 1 day ago
            homeScore = 3,
            awayScore = 2,
            homeTeamColorArgb = android.graphics.Color.GREEN,
            awayTeamColorArgb = android.graphics.Color.MAGENTA, // Using MAGENTA for Purple
            venue = "Old Trafford (simulated)",
        ),
        Game.defaults().copy(
            id = "completedGame2",
            homeTeamName = "Black Cats",
            awayTeamName = "White Knights",
            status = GameStatus.COMPLETED,
            currentPhase = GamePhase.GAME_ENDED,
            gameDateTimeEpochMillis = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L), // 5 days ago
            homeScore = 0,
            awayScore = 0,
            homeTeamColorArgb = android.graphics.Color.BLACK,
            awayTeamColorArgb = android.graphics.Color.WHITE,
            venue = "The Den",
        ),
        // Your example item (slightly adjusted if `halfDurationMinutes` affects display directly)
        Game.defaults().copy(
            id = "previewGameYourExample",
            status = GameStatus.COMPLETED, // Assuming if it has score and time, it's active
            currentPhase = GamePhase.GAME_ENDED,
            homeTeamName = "Red Team Example",
            awayTeamName = "Blue Team Example",
            homeTeamColorArgb = android.graphics.Color.BLACK, // As per your example
            awayTeamColorArgb = android.graphics.Color.YELLOW,
            kickOffTeam = Team.AWAY,
            actualTimeElapsedInPeriodMillis = (5 * 60000L), // 5 minutes (assuming your (5*L)+(2*L) was an example)
            displayedTimeMillis = (45 * 60000L) - (5*60000L), // If half is 45m, and 5m elapsed, 40m displayed (countdown)
            halfDurationMinutes = 45,
            homeScore = 2,
            isTimerRunning = true // Implied if it's FIRST_HALF with time elapsed
        )
    )
}

@Preview(
    device = "id:wearos_large_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true,
    name = "GameList - Empty Scheduled"
)
@Composable
fun GameListScreenPreview_EmptyScheduled() {
    val mockViewModel = FakeWearGameViewModel(
        initialGames = emptyList(),
        initialFetchStatus = DataFetchStatus.NO_DATA_AVAILABLE // Or SUCCESS if list is just empty
    )
    // RefWatchTheme { // Wrap in your app's theme
    GameListScreen(
        viewModel = mockViewModel,
        onGameSelected = {},
        onViewLog = {},
        onNavigateToNewGame = {},
        onNavigateToGameScreen = {}
    )
    // }
}

@Preview(
    device = "id:wearos_large_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true,
    name = "GameList - Loading State"
)
@Composable
fun GameListScreenPreview_Loading() {
    val mockViewModel = FakeWearGameViewModel(
        initialGames = emptyList(),
        initialFetchStatus = DataFetchStatus.FETCHING
    )
    // RefWatchTheme {
    GameListScreen(
        viewModel = mockViewModel,
        onGameSelected = {},
        onViewLog = {},
        onNavigateToNewGame = {},
        onNavigateToGameScreen = {}
    )
    // }
}

@Preview(
    device = "id:wearos_large_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true,
    name = "GameList - Error State"
)
@Composable
fun GameListScreenPreview_Error() {
    val mockViewModel = FakeWearGameViewModel(
        initialGames = emptyList(),
        initialFetchStatus = DataFetchStatus.ERROR_PHONE_UNREACHABLE
    )
    // RefWatchTheme {
    GameListScreen(
        viewModel = mockViewModel,
        onGameSelected = {},
        onViewLog = {},
        onNavigateToNewGame = {},
        onNavigateToGameScreen = {}
    )
    // }
}

@Preview(
    device = "id:wearos_small_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true,
    name = "GameList - With Scheduled Games"
)
@Composable
fun GameListScreenPreview_WithScheduledGames() {
    val mockViewModel = FakeWearGameViewModel(initialGames = createSampleGames().filter { it.status == GameStatus.SCHEDULED })
    // RefWatchTheme {
    GameListScreen(
        viewModel = mockViewModel,
        onGameSelected = {},
        onViewLog = {},
        onNavigateToNewGame = {},
        onNavigateToGameScreen = {}
    )
    // }
}

@Preview(
    device = "id:wearos_large_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true,
    name = "GameList - With Past Games Tab"
)
@Composable
fun GameListScreenPreview_WithPastGames() {
    // To see the "Past" tab selected, we'd ideally need a way to control
    // the internal 'selectedTab' state of GameListScreen from the preview,
    // or the FakeWearGameViewModel could expose a way to hint the initial tab.
    // For simplicity, this preview will show the games, but the "Scheduled" tab will be selected by default.
    // To preview the "Past" tab selected, you'd need to modify GameListScreen or its state handling for previews.
    val mockViewModel = FakeWearGameViewModel(initialGames = createSampleGames())
    // RefWatchTheme {
    GameListScreen(
        viewModel = mockViewModel,
        onGameSelected = {},
        onViewLog = {},
        onNavigateToNewGame = {},
        onNavigateToGameScreen = {}
    )
    // }
}

@Preview(
    device = "id:wearos_large_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true,
    name = "GameList - With Resumable Game"
)
@Composable
fun GameListScreenPreview_WithResumableGame() {
    val resumableGame = Game(
        id = "active123",
        homeTeamName = "Active Team A",
        awayTeamName = "Active Team B",
        isTimerRunning = true,
        displayedTimeMillis = 120000 // 2 minutes in
    )
    val mockViewModel = FakeWearGameViewModel(
        initialGames = createSampleGames(),
        initialActiveGame = resumableGame
    )
    // RefWatchTheme {
    GameListScreen(
        viewModel = mockViewModel,
        onGameSelected = {},
        onViewLog = {},
        onNavigateToNewGame = {},
        onNavigateToGameScreen = {}
    )
    // }
}

