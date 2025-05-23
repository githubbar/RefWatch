package com.databelay.refwatch.wear.navigation

// Navigation Routes
sealed class Screen(val route: String) {
    object Home : Screen("home_screen") // New Home Screen
    object PreGameSetup : Screen("pre_game_setup_screen")
    object KickOffSelection : Screen("kick_off_selection_screen")
    object Game : Screen("game_screen")
    object LogCard : Screen("log_card_screen") // << ADD THIS ENTRY
    object GameLog : Screen("game_log_screen")
    object GameSchedule : Screen("game_schedule_screen") // New
    object LoadIcs : Screen("load_ics_screen")         // New

    // Helper function to append arguments (optional, but can be useful)
    // fun withArgs(vararg args: String): String {
    //    return buildString {
    //        append(route)
    //        args.forEach { arg ->
    //            append("/$arg")
    //        }
    //    }
    // }
}