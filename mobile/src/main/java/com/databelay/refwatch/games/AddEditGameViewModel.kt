package com.databelay.refwatch.games // Or a subpackage like com.databelay.refwatch.games.addedit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.databelay.refwatch.common.*
import com.databelay.refwatch.common.theme.DefaultAwayJerseyColor
import com.databelay.refwatch.common.theme.DefaultHomeJerseyColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

// Data class to hold the UI state of the form
data class AddEditGameUiState(
    val homeTeamName: String = "Home",
    val awayTeamName: String = "Away",
    val venue: String = "",
    val competition: String = "",
    val gameDateTimeEpochMillis: Long? = null,
    val halfDurationMinutes: Int = 45,
    val halftimeDurationMinutes: Int = 15,
    val homeTeamColorArgb: Int = DefaultHomeJerseyColor.toArgb(),
    val awayTeamColorArgb: Int = DefaultAwayJerseyColor.toArgb(),
    val kickOffTeam: Team = Team.HOME,
    val notes: String = "",
    val ageGroup: AgeGroup? = null,
    val errorMessage: String? = null,
    val isEditing: Boolean = false
)

@HiltViewModel
class AddEditGameViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle // Hilt provides this
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditGameUiState())
    val uiState: StateFlow<AddEditGameUiState> = _uiState.asStateFlow()

    private var editingGameId: String? = null

    /**
     * Populates the form with data from an existing game for editing,
     * or sets default values for a new game.
     */
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
                ageGroup = gameToEdit.ageGroup,
                isEditing = true
            )
        } else {
            // New game, initialize with defaults
            _uiState.value = AddEditGameUiState(
                // Set a default start time for new games, e.g., current time
                gameDateTimeEpochMillis = System.currentTimeMillis()
            )
        }
    }

    // --- Event Handlers for UI Inputs ---
    fun onHomeTeamNameChange(name: String) { _uiState.value = _uiState.value.copy(homeTeamName = name) }
    fun onAwayTeamNameChange(name: String) { _uiState.value = _uiState.value.copy(awayTeamName = name) }
    fun onVenueChange(venue: String) { _uiState.value = _uiState.value.copy(venue = venue) }
    fun onGameDateTimeChange(epochMillis: Long?) { _uiState.value = _uiState.value.copy(gameDateTimeEpochMillis = epochMillis) }
    fun onHalfDurationChange(minutes: String) { _uiState.value = _uiState.value.copy(halfDurationMinutes = minutes.toIntOrNull() ?: 45) }
    fun onHalftimeDurationChange(minutes: String) { _uiState.value = _uiState.value.copy(halftimeDurationMinutes = minutes.toIntOrNull() ?: 15) }
    fun onHomeColorSelected(color: Color) { _uiState.value = _uiState.value.copy(homeTeamColorArgb = color.toArgb()) }
    fun onAwayColorSelected(color: Color) { _uiState.value = _uiState.value.copy(awayTeamColorArgb = color.toArgb()) }
    fun onKickOffTeamSelected(team: Team) { _uiState.value = _uiState.value.copy(kickOffTeam = team) }
    fun onNotesChanged(newNotes: String) { _uiState.value = _uiState.value.copy(notes = newNotes) }

    /**
     * Validates the current UI state and constructs a Game object,
     * then passes it to the onGameSaved callback.
     */
    fun onSaveGame(onGameSaved: (Game) -> Unit) {
        val currentState = _uiState.value
        if (currentState.homeTeamName.isBlank() || currentState.awayTeamName.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Team names cannot be empty.")
            return
        }
        _uiState.value = currentState.copy(errorMessage = null)

        // Construct the final Game object from the form's state
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
            ageGroup = currentState.ageGroup,
            lastUpdated = System.currentTimeMillis()
        )
        onGameSaved(game)
    }
}