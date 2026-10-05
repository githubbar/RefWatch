package com.databelay.refwatch.common

/**
 * Which games the watch loads and lists: upcoming games and games from the last two weeks.
 *
 * Downloading every game made a fresh watch's first Firestore load stall, because completed
 * games carry their recorded GPS, heart-rate and step history. The watch listens to two queries,
 * games not yet ended ([PHASE_FIELD] != [ENDED_PHASE]) and games starting after [recentCutoff]
 * ([DATE_FIELD]), and [isShown] keeps the ones inside the window. That also drops games left
 * "in progress" long ago, which were abandoned rather than refereed.
 */
object WatchGames {
    const val PHASE_FIELD = "currentPhase"
    val ENDED_PHASE: String = GamePhase.GAME_ENDED.name
    const val DATE_FIELD = "gameDateTimeEpochMillis"

    const val RECENT_MILLIS = 14 * 24 * 60 * 60 * 1000L

    fun recentCutoff(nowMillis: Long): Long = nowMillis - RECENT_MILLIS

    /** Games without a kickoff time (created on the watch) are dated by their last update. */
    fun isShown(game: Game, nowMillis: Long): Boolean =
        (game.gameDateTimeEpochMillis ?: game.lastUpdated) >= recentCutoff(nowMillis)
}
