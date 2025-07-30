package com.databelay.refwatch.common

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlinx.serialization.json.Json

// If gameEventModule is also defined in the common module, you can refer to it directly.
// Otherwise, ensure it's accessible or pass it as a parameter if it varies.
// For simplicity, let's assume gameEventModule is also a top-level val in the common module.

/**
 * The globally shared Json instance for serializing and deserializing
 * game data and events consistently across all modules (mobile, wear, common).
 *
 * It is configured for polymorphic serialization of GameEvent using a
 * class discriminator and includes the necessary serializers module.
 */
val AppJsonConfiguration: Json = Json {
    // prettyPrint is useful for debugging, can be false for release builds/data transfer
    // to save space. Consider making this configurable if needed.
    prettyPrint = true // Set to false if you want to optimize for size in transfers

    // This MUST match on both serialization (sending) and deserialization (receiving) ends.
    classDiscriminator = "eventType"

    // Important for compatibility if new fields are added to data classes
    ignoreUnknownKeys = true

    // Ensures that properties with default values are included in the JSON output.
    // Useful if the receiving end relies on these defaults being present.
    encodeDefaults = true

    // The module that contains all serializers for GameEvent subclasses
    // and any other custom serializers needed by Game or its properties.
    serializersModule = gameEventModule // Make sure gameEventModule is accessible here
}

// Helper function to convert a Map<String, Any?> from Firestore to JsonObject
// This needs to handle various types Firestore might return
fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
    return buildJsonObject {
        map.forEach { (key, value) ->
            put(key, anyToJsonElement(value))
        }
    }
}
// Helper function to convert JsonElement to Any? for Firestore compatibility
// Make sure this is part of your GameRepository class or accessible to it.
fun jsonElementToAny(jsonElement: JsonElement): Any? {
    return when (jsonElement) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (jsonElement.isString) jsonElement.content
            else if (jsonElement.content == "true" || jsonElement.content == "false") jsonElement.content.toBoolean()
            else jsonElement.content.toDoubleOrNull() ?: jsonElement.content.toLongOrNull() ?: jsonElement.content // Fallback to string if not clearly number/boolean
        }
        is JsonObject -> jsonObjectToMap(jsonElement)
        is JsonArray -> jsonArrayToList(jsonElement)
    }
}

// Helper function to convert JsonObject to Map<String, Any?>
fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any?> {
    return jsonObject.entries.associate { (key, jsonElement) ->
        key to jsonElementToAny(jsonElement)
    }
}

// Helper function to convert JsonArray to List<Any?>
fun jsonArrayToList(jsonArray: JsonArray): List<Any?> {
    return jsonArray.map { jsonElementToAny(it) }
}

// Helper function to convert Any? from Firestore map value to JsonElement
fun anyToJsonElement(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value) // Handles Int, Long, Double, Float
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> {
            // Ensure keys are Strings for JsonObject
            @Suppress("UNCHECKED_CAST")
            mapToJsonObject(value as? Map<String, Any?> ?: emptyMap())
        }
        is List<*> -> buildJsonArray {
            value.forEach { item -> add(anyToJsonElement(item)) }
        }
        else -> {
            // Fallback for unknown types: try converting to string.
            // This might not be ideal for all complex types but can prevent crashes.
            // Consider logging a warning here if you hit this case often.
//                Log.w(TAG, "anyToJsonElement: Encountered an unknown type (${value::class.java.name}), converting to JsonPrimitive string: $value")
            JsonPrimitive(value.toString())
        }
    }
}
