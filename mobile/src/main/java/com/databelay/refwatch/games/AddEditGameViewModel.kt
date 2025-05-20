package com.databelay.refwatch.games // New package

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.databelay.refwatch.common.AgeGroup
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameEvent
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.theme.DefaultAwayJerseyColor
import com.databelay.refwatch.common.theme.DefaultHomeJerseyColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEditGameViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle // For passing gameId if editing
    // Potentially inject a repository if you need to fetch lists (e.g., predefined competitions)
) : ViewModel() {

    // UI State for the form
    private val _uiState = MutableStateFlow(AddEditGameUiState())
    val uiState: StateFlow<AddEditGameUiState> = _uiState.asStateFlow()

    private var editingGameId: String? = null

    init {
        // Check if we are editing an existing game (gameId passed via navigation)
        editingGameId = savedStateHandle.get<String>("gameId")
        if (editingGameId != null) {
            // TODO: If editing, load the game's details.
            // This would typically involve:
            // 1. A way to get a single game (e.g., from MobileGameViewModel's list or a new repo method)
            // 2. Populating _uiState with the loaded game's data.
            // For now, we'll assume new game creation. If you pass a Game object directly
            // via navigation (not recommended for complex objects), you'd get it here.
            // For simplicity, if editing, the Game object would be passed to initializeForm.
        } else {
            // New game, initialize with defaults
            initializeForm(null)
        }
    }

    fun initializeForm(gameToEdit: Game?) {
        if (gameToEdit != null) {
            editingGameId = gameToEdit.id
            _uiState.value = AddEditGameUiState(
                homeTeamName = gameToEdit.homeTeamName,
                awayTeamName = gameToEdit.awayTeamName,
                venue = gameToEdit.venue ?: "",
                competition = gameToEdit.competition ?: "",
                gameDateTimeEpochMillis = gameToEdit.gameDateTimeEpochMillis,
                halfDurationMinutes = gameToEdit.halfDurationMinutes,
                halftimeDurationMinutes = gameToEdit.halftimeDurationMinutes,
                homeTeamColorArgb = gameToEdit.homeTeamColorArgb,
                awayTeamColorArgb = gameToEdit.awayTeamColorArgb,
                kickOffTeam = gameToEdit.kickOffTeam,
                notes = gameToEdit.notes ?: "",
                isEditing = true
                // ageGroup = gameToEdit.ageGroup // You'd need to handle AgeGroup state
            )
        } else {
            // Defaults for a new game
            _uiState.value = AddEditGameUiState()
        }
    }


    // --- Event Handlers for Form Inputs ---
    fun onHomeTeamNameChange(name: String) {
        _uiState.value = _uiState.value.copy(homeTeamName = name)
    }

    fun onAwayTeamNameChange(name: String) {
        _uiState.value = _uiState.value.copy(awayTeamName = name)
    }

    fun onVenueChange(venue: String) {
        _uiState.value = _uiState.value.copy(venue = venue)
    }
    // ... Add similar handlers for all other fields (competition, date, time, durations, colors, notes) ...

    fun onGameDateTimeChange(epochMillis: Long?) {
        _uiState.value = _uiState.value.copy(gameDateTimeEpochMillis = epochMillis)
    }
    fun onHalfDurationChange(minutes: String) {
        _uiState.value = _uiState.value.copy(halfDurationMinutes = minutes.toIntOrNull() ?: _uiState.value.halfDurationMinutes)
    }
    fun onHalftimeDurationChange(minutes: String) {
        _uiState.value = _uiState.value.copy(halftimeDurationMinutes = minutes.toIntOrNull() ?: _uiState.value.halftimeDurationMinutes)
    }
    fun onHomeColorSelected(color: Color) {
        _uiState.value = _uiState.value.copy(homeTeamColorArgb = color.toArgb())
    }
    fun onAwayColorSelected(color: Color) {
        _uiState.value = _uiState.value.copy(awayTeamColorArgb = color.toArgb())
    }
    fun onKickOffTeamSelected(team: Team) {
        _uiState.value = _uiState.value.copy(kickOffTeam = team)
    }
    fun onNotesChanged(newNotes: String) {
        _uiState.value = _uiState.value.copy(notes = newNotes)
    }


    fun onSaveGame(onGameSaved: (Game) -> Unit) {
        val currentState = _uiState.value
        // Basic Validation (add more as needed)
        if (currentState.homeTeamName.isBlank() || currentState.awayTeamName.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Team names cannot be empty.")
            return
        }
        _uiState.value = currentState.copy(errorMessage = null) // Clear error

        val game = Game(
            id = editingGameId ?: UUID.randomUUID().toString(),
            homeTeamName = currentState.homeTeamName,
            awayTeamName = currentState.awayTeamName,
            venue = currentState.venue.takeIf { it.isNotBlank() },
            competition = currentState.competition.takeIf { it.isNotBlank() },
            gameDateTimeEpochMillis = currentState.gameDateTimeEpochMillis,
            halfDurationMinutes = currentState.halfDurationMinutes,
            halftimeDurationMinutes = currentState.halftimeDurationMinutes,
            homeTeamColorArgb = currentState.homeTeamColorArgb,
            awayTeamColorArgb = currentState.awayTeamColorArgb,
            kickOffTeam = currentState.kickOffTeam,
            notes = currentState.notes.takeIf { it.isNotBlank() },
            lastUpdated = System.currentTimeMillis(),
            // Ensure other fields from Game data class are initialized (e.g., currentPhase, score, etc. to defaults)
            currentPhase = if (editingGameId != null) uiState.value.currentPhase else GamePhase.PRE_GAME, // Preserve if editing
            homeScore = if (editingGameId != null) uiState.value.homeScore else 0,
            awayScore = if (editingGameId != null) uiState.value.awayScore else 0,
            events = if (editingGameId != null) uiState.value.events else emptyList(),
            ageGroup = currentState.ageGroup // Handle age group selection
            // ... initialize other Game fields ...
        )
        onGameSaved(game) // Pass the created/updated game back
    }
}

// Data class for the UI state of the Add/Edit screen
data class AddEditGameUiState(
    val homeTeamName: String = "Home",
    val awayTeamName: String = "Away",
    val venue: String = "",
    val competition: String = "",
    val gameDateTimeEpochMillis: Long? = null, // Store as Long
    val halfDurationMinutes: Int = 45,
    val halftimeDurationMinutes: Int = 15,
    val homeTeamColorArgb: Int = DefaultHomeJerseyColor.toArgb(),
    val awayTeamColorArgb: Int = DefaultAwayJerseyColor.toArgb(),
    val kickOffTeam: Team = Team.HOME,
    val notes: String = "",
    val ageGroup: AgeGroup? = null, // You'll need a way to select this
    val errorMessage: String? = null,
    val isEditing: Boolean = false,

    // Fields from Game that should be preserved if editing, but not directly part of initial form for new game
    val currentPhase: GamePhase = GamePhase.PRE_GAME,
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val events: List<GameEvent> = emptyList()

    // ... any other fields needed for the form state ...
)