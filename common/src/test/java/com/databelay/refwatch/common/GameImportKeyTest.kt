package com.databelay.refwatch.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class GameImportKeyTest {
    private val start = LocalDateTime.parse("2026-10-10T13:00")
    private val park = "2450 S. Endwright Road, Bloomington, IN 47403"

    @Test
    fun sameDateAndGameNumberMatchEvenIfTheTimeMoved() {
        assertEquals(
            GameImportKey.of("179", start, park),
            GameImportKey.of("179", start.withHour(15), "Somewhere else")
        )
    }

    @Test
    fun gameNumbersRepeatAcrossSeasons() {
        // Rec league game #132 exists in Sept 2024 and Oct 2026.
        assertNotEquals(
            GameImportKey.of("132", LocalDateTime.parse("2024-09-21T11:00"), park),
            GameImportKey.of("132", LocalDateTime.parse("2026-10-10T10:30"), park)
        )
    }

    @Test
    fun placeholderGameNumbersFallBackToStartTimeAndLocation() {
        for (placeholder in listOf(null, "", " ", "000", "XXXX")) {
            assertEquals(
                GameImportKey.of(null, start, park),
                GameImportKey.of(placeholder, start, park)
            )
        }
        assertNotEquals(GameImportKey.of(null, start, park), GameImportKey.of(null, start, "Other park"))
        assertNotEquals(GameImportKey.of(null, start, park), GameImportKey.of(null, start.plusHours(1), park))
    }

    @Test
    fun noStartTimeMeansNoKey() {
        assertNull(GameImportKey.of("179", null, park))
    }

    @Test
    fun storedGameHasTheSameKeyAsTheEventItCameFrom() {
        val event = SimpleIcsEvent(uid = "export-uid", dtStart = start, gameNumber = "179", location = park)
        assertEquals(GameImportKey.of(event), GameImportKey.of(Game(event)))

        val noNumber = SimpleIcsEvent(uid = "other-uid", dtStart = start, gameNumber = null, location = park)
        assertEquals(GameImportKey.of(noNumber), GameImportKey.of(Game(noNumber)))
    }

    @Test
    fun newEventsSkipGamesAlreadyStored() {
        val imported = SimpleIcsEvent(uid = "old-export-uid", dtStart = start, gameNumber = "179", location = park)
        // An older import, stored under its export UID.
        val storedByFields = Game(imported)
        // A newer import, stored under the key-derived id; its fields were since edited.
        val other = SimpleIcsEvent(uid = "x", dtStart = start.plusDays(1), gameNumber = "180", location = park)
        val storedById = Game(other).copy(
            id = GameImportKey.documentId(GameImportKey.of(other)!!),
            gameNumber = "edited"
        )

        val reExport = listOf(
            imported.copy(uid = "new-export-uid-1"),
            other.copy(uid = "new-export-uid-2"),
            SimpleIcsEvent(uid = "fresh", dtStart = start.plusDays(2), gameNumber = "181", location = park),
            // The same new game twice in one file is imported once.
            SimpleIcsEvent(uid = "fresh-dup", dtStart = start.plusDays(2), gameNumber = "181", location = park)
        )

        assertEquals(listOf("fresh"), GameImportKey.newEvents(reExport, listOf(storedByFields, storedById)).map { it.uid })
    }

    @Test
    fun documentIdIsStableAndFirestoreSafe() {
        val key = GameImportKey.of(null, start, "Field 3 / West Park")!!
        val id = GameImportKey.documentId(key)
        assertEquals(id, GameImportKey.documentId(key))
        assertTrue(id, id.matches(Regex("ics-[0-9a-f]{24}")))
    }
}
