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

    @Test
    fun parsing_isCorrect() {
        val icsEvents: List<SimpleIcsEvent>? = test_Parsing()
        if (icsEvents != null) {
            for (e: SimpleIcsEvent in icsEvents)
                println(e.toString())
        }

/*
        if (icsEvents != null) {
            if (icsEvents.isNotEmpty()) {
                println("\n--- Parsed Events ---")
                icsEvents.forEach { event -> println(event) }
            } else {
                println("No events were parsed from the ICS data.")
            }
        } else {
            println("Failed to parse ICS data (icsEvents list is null).")
        }
*/

//        assertEquals(test_Parsing(), "")
    }
}
