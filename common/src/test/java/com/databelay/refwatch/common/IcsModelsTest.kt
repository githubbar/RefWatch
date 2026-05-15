package com.databelay.refwatch.common

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream
import org.junit.After
import org.junit.Before
import org.mockito.MockedStatic

// Make sure this matches your actual package

// Function to read content from a resource file
fun readResourceFile(fileName: String): String? {
    // Try to get the resource as a stream from the classloader
    val inputStream: InputStream? = IcsModelsTest::class.java.classLoader?.getResourceAsStream(fileName)
    return inputStream?.bufferedReader()?.use { it.readText() }
}

fun test_Parsing(icsFileName: String = "test_calendar.ics"): List<SimpleIcsEvent>? { // Added parameter with default
    println("Script started...\n")

    // --- Input Text from File ---
    val icsFileContent: String? = readResourceFile(icsFileName)

    if (icsFileContent == null) {
        println("Error: Could not read the ICS file: $icsFileName")
        return null
    }
    val icsEvents: List<SimpleIcsEvent>? =
        SimpleIcsParser.parse(icsFileContent)
    return icsEvents
}

class IcsModelsTest {
    private var logMock: MockedStatic<Log>? = null

    @Before
    fun setUp() {
        logMock = org.mockito.Mockito.mockStatic(Log::class.java)
    }

    @After
    fun tearDown() {
        logMock?.close()
    }

    @Test
    fun parsing_isCorrect() {
        val icsEvents: List<SimpleIcsEvent>? = test_Parsing()
        if (icsEvents != null) {
            for (e: SimpleIcsEvent in icsEvents)
                println(e.toString())
        }
    }

    @Test
    fun test2026GamesFromDownloads() {
        val path = "C:/Users/oleyk/Downloads/referee_assignments(1).ics"
        val file = java.io.File(path)
        if (!file.exists()) {
            println("File not found at $path")
            return
        }
        val content = file.readText()
        val events = SimpleIcsParser.parse(content)
        println("\n--- 2026 Games Age Group Detection ---")
        events.filter { it.dtStart?.year == 2026 }
              .forEach { event ->
            println("Date: ${event.dtStart?.toLocalDate()} | Game ${event.gameNumber}: ${event.homeTeam} vs ${event.awayTeam} -> Age Group: ${event.ageGroup?.displayName}")
        }
    }
}
