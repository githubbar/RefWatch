package com.databelay.refwatch.wear.navigation

import com.databelay.refwatch.common.Team

/**
 * A singleton object that holds all the navigation route constants and
 * helper functions for the Wear OS app, mirroring the mobile app's style.
 */
object WearNavRoutes {

    // --- Argument Keys ---
    // Define argument names as constants to avoid typos
    const val GAME_ID_ARG = "gameId"
    const val TEAM_ARG = "team"

    // --- Route Definitions ---
    // Base routes are simple constants
    const val GAME_LIST_SCREEN = "game_list"
    const val PRE_GAME_SETUP_SCREEN = "pre_game_setup"
    const val KICK_OFF_SELECTION_SCREEN = "kick_off_selection"
    const val GAME_IN_PROGRESS_SCREEN = "game_in_progress"
    const val GAME_LOG_SCREEN = "game_log"
    const val LOG_CARD_SCREEN = "log_card/{$TEAM_ARG}"
    // --- Route Helper Functions ---

    /**
     * Creates the navigation route for the active game screen.
     * This route requires a gameId.
     */
    fun gameInProgressRoute(gameId: String): String {
        return "$GAME_IN_PROGRESS_SCREEN?$GAME_ID_ARG=$gameId"
    }

    /**
     * Creates the navigation route for the game log screen.
     * This route requires a gameId.
     */
    fun gameLogRoute(gameId: String): String {
        return "$GAME_LOG_SCREEN?$GAME_ID_ARG=$gameId"
    }

    /**
     * Creates the navigation route for the log card screen.
     * This route requires a team to be pre-selected.
     */
    fun logCardRoute(team: Team): String = "log_card/${team.name}"

}