package com.databelay.refwatch.common // Make sure this matches your actual package

import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail // For throwing AssertionError directly
import org.junit.Test
import java.util.UUID

// --- Data classes for testing ---

@Serializable
enum class TestEnum {
    ALPHA, BRAVO, CHARLIE
}

@Serializable
data class SimpleTestData(
    val id: Int,
    val name: String,
    val isActive: Boolean,
    val items: List<String>
)

@Serializable
data class ComplexTestData(
    val data: SimpleTestData,
    val type: TestEnum,
    val description: String? = null
)

@Serializable
sealed class BaseEvent : Parcelable {
    abstract val eventId: String
    abstract val eventTimestamp: Long
    abstract val notes: String?

    abstract val id: String
    abstract val timestamp: Long // Wall-clock time of event logging
    abstract val gameTimeMillis: Long // Game clock time when event occurred
    abstract val displayString: String // User-friendly string for the log
}

@Serializable
@SerialName("SIMPLE_LOG")
@Parcelize
data class SimpleLogEvent(
    override val eventId: String = "log-" + UUID.randomUUID().toString().take(8),
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val eventTimestamp: Long = System.currentTimeMillis(),
    override val gameTimeMillis: Long = 0L,
    val message: String,
    val level: Int,
    override val notes: String? = "Default log notes"

) : BaseEvent() {
    @get:Exclude
    override val displayString: String
        get() = message
}

@Serializable
@SerialName("ACTION_PERFORMED")
@Parcelize
data class ActionEvent(
    override val eventId: String = "action-" + UUID.randomUUID().toString().take(8),
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val eventTimestamp: Long = System.currentTimeMillis(),
    override val gameTimeMillis: Long = 0L,
    val actionName: String,
    val targetId: String,
    val success: Boolean,
    override val notes: String? = null
) : BaseEvent() {
    @get:Exclude
    override val displayString: String
        get() = "action"
}
@Serializable
@SerialName("STATUS_UPDATE")
@Parcelize

data class StatusUpdateEvent(
    override val eventId: String = "status-" + UUID.randomUUID().toString().take(8),
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val eventTimestamp: Long = System.currentTimeMillis(),
    override val gameTimeMillis: Long = 0L,
    val newStatus: TestEnum,
    override val notes: String? = "Status changed"
) : BaseEvent() {
    @get:Exclude
    override val displayString: String
        get() = "action"
}

class SerializationTest {
    private val polymorphicKtxJson = Json {
        prettyPrint = true
        classDiscriminator = "eventTypeAlias"
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        isLenient = true // Can be helpful

//        serializersModule = SerializersModule {
//            polymorphic(BaseEvent::class) {
//                subclass(SimpleLogEvent::class)
//                subclass(ActionEvent::class)
//                subclass(StatusUpdateEvent::class)
//            }
//        }
    }

    private val simpleJson = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true // Important for testing null serialization
        ignoreUnknownKeys = true // Good practice
    }

    @Test
    fun testSimpleLogEventSerialization() {
        val logEventData: BaseEvent = SimpleLogEvent(
            message = "Test log message",
            level = 1
        )
        try {
            val jsonString = polymorphicKtxJson.encodeToString(logEventData)
            println("Serialized SimpleLogEvent (as BaseEvent): $jsonString")

            assertNotNull("JSON string should not be null", jsonString)
            assertTrue(
                "JSON string should contain discriminator 'eventTypeAlias'",
                jsonString.contains("\"eventTypeAlias\": \"SIMPLE_LOG\"")
            )
            assertTrue(
                "JSON string should contain 'Test log message'",
                jsonString.contains("Test log message")
            )

            val deserializedEvent = polymorphicKtxJson.decodeFromString<BaseEvent>(jsonString)
            assertTrue(
                "Deserialized event should be SimpleLogEvent",
                deserializedEvent is SimpleLogEvent
            )
            val deserializedLogEvent = deserializedEvent as SimpleLogEvent
            assertEquals(
                "Original and deserialized messages should match",
                (logEventData as SimpleLogEvent).message,
                deserializedLogEvent.message
            )
            assertEquals(
                "Event IDs should match",
                logEventData.eventId,
                deserializedLogEvent.eventId
            )

        } catch (e: Exception) {
            e.printStackTrace()
            fail("Serialization failed for SimpleLogEvent: ${e.message}")
        }
    }

    @Test
    fun testActionEventSerialization() {
        val actionEventData: BaseEvent = ActionEvent(
            actionName = "ButtonClicked",
            targetId = "submitButton",
            success = true,
            notes = "User clicked submit"
        )
        try {
            val jsonString = polymorphicKtxJson.encodeToString(actionEventData)
            println("Serialized ActionEvent (as BaseEvent): $jsonString")

            assertNotNull("JSON string should not be null", jsonString)
            assertTrue(
                "JSON string should contain discriminator 'eventTypeAlias'",
                jsonString.contains("\"eventTypeAlias\": \"ACTION_PERFORMED\"")
            )
            assertTrue(
                "JSON string should contain 'ButtonClicked'",
                jsonString.contains("ButtonClicked")
            )
            assertTrue(
                "JSON string should contain 'User clicked submit'",
                jsonString.contains("User clicked submit")
            )

            val deserializedEvent = polymorphicKtxJson.decodeFromString<BaseEvent>(jsonString)
            assertTrue("Deserialized event should be ActionEvent", deserializedEvent is ActionEvent)
            val deserializedActionEvent = deserializedEvent as ActionEvent
            assertEquals(
                "Original and deserialized actionName should match",
                (actionEventData as ActionEvent).actionName,
                deserializedActionEvent.actionName
            )
            assertEquals("Notes should match", actionEventData.notes, deserializedActionEvent.notes)

        } catch (e: Exception) {
            e.printStackTrace()
            fail("Serialization failed for ActionEvent: ${e.message}")
        }
    }

    @Test
    fun testListOfMixedEventsSerialization() {
        val originalEvents: List<BaseEvent> = listOf(
            SimpleLogEvent(message = "First event", level = 0),
            ActionEvent(
                actionName = "DataLoaded",
                targetId = "listView",
                success = true,
                notes = null
            ),
            StatusUpdateEvent(newStatus = TestEnum.CHARLIE, notes = "System is now Charlie")
        )

        try {
            val jsonString = polymorphicKtxJson.encodeToString(originalEvents)
            println("Serialized List<BaseEvent>: $jsonString")

            assertNotNull("JSON string should not be null", jsonString)
            // General checks for discriminators and key content
            assertTrue(
                "JSON string should contain 'SIMPLE_LOG' type",
                jsonString.contains("\"eventTypeAlias\": \"SIMPLE_LOG\"")
            )
            assertTrue(
                "JSON string should contain 'ACTION_PERFORMED' type",
                jsonString.contains("\"eventTypeAlias\": \"ACTION_PERFORMED\"")
            )
            assertTrue(
                "JSON string should contain 'STATUS_UPDATE' type",
                jsonString.contains("\"eventTypeAlias\": \"STATUS_UPDATE\"")
            )
            assertTrue(
                "JSON string should contain 'First event'",
                jsonString.contains("First event")
            )
            assertTrue("JSON string should contain 'DataLoaded'", jsonString.contains("DataLoaded"))

            // Robust check for null notes in the specific ActionEvent
            val deserializedEvents =
                polymorphicKtxJson.decodeFromString<List<BaseEvent>>(jsonString)
            assertEquals(
                "Deserialized list size should match original",
                originalEvents.size,
                deserializedEvents.size
            )

            val actionEventFromList =
                deserializedEvents.find { it is ActionEvent && it.actionName == "DataLoaded" } as? ActionEvent
            assertNotNull(
                "Action event 'DataLoaded' should be found in the deserialized list",
                actionEventFromList
            )

            // Assert on the deserialized object for null notes
            assertEquals(
                "Deserialized ActionEvent 'DataLoaded' should have null notes",
                null,
                actionEventFromList?.notes
            )

            // Assert that the original ActionEvent also had null notes, for test validity
            val originalActionEvent =
                originalEvents.find { it is ActionEvent && it.actionName == "DataLoaded" } as? ActionEvent
            assertEquals(
                "Original ActionEvent 'DataLoaded' should have null notes for this test",
                null,
                originalActionEvent?.notes
            )

            // Optional: More targeted check on the raw JSON string for the "notes":null part
            if (polymorphicKtxJson.configuration.explicitNulls) {
                val jsonArrayElement = polymorphicKtxJson.parseToJsonElement(jsonString).jsonArray
                val actionEventJsonElement = jsonArrayElement.find {
                    it.jsonObject["eventTypeAlias"]?.jsonPrimitive?.content == "ACTION_PERFORMED" &&
                            it.jsonObject["actionName"]?.jsonPrimitive?.content == "DataLoaded"
                }
                assertNotNull(
                    "JSON element for ActionEvent 'DataLoaded' should be found",
                    actionEventJsonElement
                )
                assertTrue(
                    "JSON string for ActionEvent 'DataLoaded' should contain '\"notes\":null'",
                    actionEventJsonElement.toString().contains("\"notes\":null")
                )
            }

            // Check types of deserialized events
            assertTrue(
                "First deserialized event should be SimpleLogEvent",
                deserializedEvents[0] is SimpleLogEvent
            )
            assertTrue(
                "Second deserialized event should be ActionEvent",
                deserializedEvents[1] is ActionEvent
            )
            assertTrue(
                "Third deserialized event should be StatusUpdateEvent",
                deserializedEvents[2] is StatusUpdateEvent
            )

            // Verify the notes of the specific ActionEvent after deserialization (redundant if above checks pass, but good for sanity)
            assertEquals(
                (originalEvents[1] as ActionEvent).notes,
                (deserializedEvents[1] as ActionEvent).notes
            )

        } catch (e: Exception) {
            e.printStackTrace()
            fail("Serialization failed for List<BaseEvent>: ${e.message}")
        }
    }

    @Test
    fun testSimpleTestDataSerialization() {
        val originalData = SimpleTestData(
            id = 1,
            name = "Test Item",
            isActive = true,
            items = listOf("A", "B", "C")
        )
        try {
            val jsonString = simpleJson.encodeToString(originalData)
            println("Serialized SimpleTestData: $jsonString")
            val deserializedData = simpleJson.decodeFromString<SimpleTestData>(jsonString)
            assertEquals(originalData, deserializedData)
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Serialization failed for SimpleTestData: ${e.message}")
        }
    }

    // Test method
    @Test
    fun testComplexTestDataSerializationWithNullDescription() {
        val simple = SimpleTestData(3, "Another", true, listOf("X"))
        val originalComplexData = ComplexTestData(
            data = simple,
            type = TestEnum.CHARLIE,
            description = null // Explicitly setting description to null
        )
        try {
            val jsonString = simpleJson.encodeToString(originalComplexData)
            println("Serialized ComplexTestData (null desc): $jsonString")

            // Primary assertion: Check the property on the deserialized object
            val deserializedData = simpleJson.decodeFromString<ComplexTestData>(jsonString)
            assertEquals(
                "Deserialized ComplexTestData should have a null description property",
                null,
                deserializedData.description
            )

            // Overall equality check for the deserialized object
            assertEquals(
                "Deserialized complex data should match original overall",
                originalComplexData,
                deserializedData
            )

            // --- Replacement for the previous jsonString.contains() check ---
            // This block now verifies the specific JSON structure for 'description' being null,
            // if explicitNulls is true.
            if (simpleJson.configuration.explicitNulls) {
                val jsonElement = simpleJson.parseToJsonElement(jsonString)
                val descriptionJsonValue = jsonElement.jsonObject["description"]

                assertNotNull(
                    "When explicitNulls=true, 'description' field should exist in the JSON object. Actual JSON: $jsonString",
                    descriptionJsonValue
                )

                // We expect the JSON value to be JsonNull
                assertEquals(
                    "When explicitNulls=true, the 'description' field in JSON should be 'JsonNull'. Actual value: ${descriptionJsonValue?.toString()}. JSON: $jsonString",
                    JsonNull, // The expected JSON null instance
                    descriptionJsonValue
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            fail("Serialization failed for ComplexTestData (null desc): ${e.message}")
        }
    }
}
