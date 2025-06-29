package com.databelay.refwatch.navigation


// Define your navigation routes if you haven't already
object MobileNavRoutes {
    const val LOADING_SCREEN = "loading"
    const val AUTH_SCREEN = "auth"
    const val GAME_LIST_SCREEN = "game_list"
    const val ADD_EDIT_GAME_SCREEN = "add_edit_game_screen" // For navigating to Add/Edit
    fun addEditGameRoute(gameId: String? = null): String {
        return if (gameId != null) "$ADD_EDIT_GAME_SCREEN?gameId=$gameId" else ADD_EDIT_GAME_SCREEN
    }
    const val GAME_LOG_SCREEN = "game_log"
    fun gameLogRoute(gameId: String? = null): String {
        return if (gameId != null) "$GAME_LOG_SCREEN?gameId=$gameId" else GAME_LOG_SCREEN
    }
}