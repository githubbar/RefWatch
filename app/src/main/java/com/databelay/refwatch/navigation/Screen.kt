package com.databelay.refwatch.navigation

// Navigation Routes
sealed class Screen(val route: String) {
    object PreGameSetup : Screen("pre_game_setup_screen")
    object KickOffSelection : Screen("kick_off_selection_screen")
    object HomeColorSelection : Screen("home_color_selection_screen")
    object AwayColorSelection : Screen("away_color_selection_screen")
    object DurationSettings : Screen("duration_settings_screen")
    object Game : Screen("game_screen")
//    object GameSettingsDialog : Screen("game_settings_screen")
    object LogCard : Screen("log_card_screen") // << ADD THIS ENTRY
    object GameLog : Screen("game_log_screen")

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