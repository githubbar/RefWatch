package com.databelay.refwatch.wear.navigation

import com.databelay.refwatch.common.Team

// Navigation Routes
sealed class Screen(val route: String) {
    object Home : Screen("game_schedule_screen") // New Home Screen
    object PreGameSetup : Screen("pre_game_setup_screen")
    object KickOffSelection : Screen("kick_off_selection_screen")
    object Game : Screen("game_screen")
    object GameLog : Screen("game_log_screen")
    object GameSchedule : Screen("game_schedule_screen") // New
    object LoadIcs : Screen("load_ics_screen")         // New

    // Update LogCard to handle a required "team" argument
    object LogCard : Screen("log_card/{team}") {
        fun createRoute(team: Team): String = "log_card/${team.name}"
    }
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