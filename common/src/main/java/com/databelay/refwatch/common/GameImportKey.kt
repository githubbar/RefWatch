package com.databelay.refwatch.common

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Identifies a game across calendar exports. Assigning sites regenerate event UIDs on every
 * export, so the UID cannot tell an already-imported game from a new one.
 *
 * The key is the game date plus the assignor's game number: unique across real exports, and it
 * still matches when the assignor moves the kickoff within the day. Game numbers repeat across
 * seasons, which the date resolves. Without a game number it falls back to the exact start time
 * plus location.
 */
object GameImportKey {
    // Values the parser and Game use to mean "no game number".
    private val PLACEHOLDER_NUMBERS = setOf("000", "XXXX")

    fun of(gameNumber: String?, start: LocalDateTime?, location: String?): String? {
        if (start == null) return null
        val number = gameNumber?.trim()?.takeUnless { it.isEmpty() || it in PLACEHOLDER_NUMBERS }
        return if (number != null) {
            "${start.toLocalDate()}#$number"
        } else {
            "${start.withSecond(0).withNano(0)}@${location?.trim()?.lowercase().orEmpty()}"
        }
    }

    fun of(event: SimpleIcsEvent): String? = of(event.gameNumber, event.dtStart, event.location)

    fun of(game: Game): String? = of(
        game.gameNumber,
        game.gameDateTimeEpochMillis?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) },
        game.venue
    )

    /**
     * The events in [events] that are not already among [stored], each game once. A stored game
     * matches by its key-derived id (games imported since keys existed, even if edited later) or
     * by the key of its fields (games imported earlier under an export UID).
     */
    fun newEvents(events: List<SimpleIcsEvent>, stored: List<Game>): List<SimpleIcsEvent> {
        val storedIds = stored.mapTo(HashSet()) { it.id }
        val storedKeys = stored.mapNotNullTo(HashSet()) { of(it) }
        val seen = HashSet<String>()
        return events.filter { event ->
            val key = of(event) ?: return@filter true // No start time: nothing to match on.
            key !in storedKeys && documentId(key) !in storedIds && seen.add(key)
        }
    }

    /** A Firestore-safe document id derived from [key], so re-importing a game targets the same document. */
    fun documentId(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return "ics-" + digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
