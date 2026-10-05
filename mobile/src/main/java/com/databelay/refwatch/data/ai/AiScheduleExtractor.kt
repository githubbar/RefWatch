package com.databelay.refwatch.data.ai

import android.util.Log
import com.databelay.refwatch.common.AgeGroup
import com.databelay.refwatch.common.SimpleIcsEvent
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads team names, role, game number, field and age group out of calendar events with Gemini,
 * using the prompt from Settings. The regex parse is the fallback: any event the model does not
 * answer for, or any field it leaves empty, keeps what [com.databelay.refwatch.common.SimpleIcsParser]
 * found.
 */
@Singleton
class AiScheduleExtractor @Inject constructor(
    private val prompts: PromptRepository
) {
    data class Outcome(
        val events: List<SimpleIcsEvent>,
        /** Events the model answered for; the rest are the regex parse unchanged. */
        val aiCount: Int,
        /** Why some or all of the import fell back to the regex parse, or null if none did. */
        val failure: String?
    )

    suspend fun enrich(events: List<SimpleIcsEvent>): Outcome {
        if (events.isEmpty()) return Outcome(events, 0, null)
        return try {
            val model = buildModel(prompts.activePrompt.value)
            // One request for a full season export takes ~30 s; parallel chunks keep it to a few.
            val answers = coroutineScope {
                events.chunked(CHUNK_SIZE).map { chunk ->
                    async {
                        try {
                            kotlin.Result.success(extract(model, chunk))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "AI extraction failed for ${chunk.size} events; using regex parse.", e)
                            kotlin.Result.failure(e)
                        }
                    }
                }.awaitAll()
            }
            val byUid = answers.flatMap { it.getOrNull().orEmpty() }.associateBy { it.uid }
            val merged = events.map { AiExtractionMerger.merge(it, byUid[it.uid]) }
            val aiCount = events.count { it.uid in byUid }
            val failure = answers.firstNotNullOfOrNull { it.exceptionOrNull() }?.let(::describe)
                ?: if (aiCount < events.size) "The AI skipped ${events.size - aiCount} events" else null
            Outcome(merged, aiCount, failure)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "AI extraction unavailable; using regex parse.", e)
            Outcome(events, 0, describe(e))
        }
    }

    private suspend fun extract(model: GenerativeModel, chunk: List<SimpleIcsEvent>): List<AiExtraction> {
        val input = buildJsonArray {
            chunk.forEach { event ->
                addJsonObject {
                    put("uid", event.uid)
                    put("start", event.dtStart?.format(START_FORMAT))
                    put("summary", event.summary)
                    put("description", event.description)
                    put("location", event.location)
                }
            }
        }
        val text = model.generateContent(input.toString()).text
            ?: throw IllegalStateException("Empty AI response")
        return AiExtractionMerger.parseResponse(text)
    }

    private fun buildModel(prompt: String): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.vertexAI(LOCATION)).generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                temperature = 0f
                responseMimeType = "application/json"
                responseSchema = RESPONSE_SCHEMA
            },
            systemInstruction = content { text(prompt) },
            requestOptions = RequestOptions(timeoutInMillis = 90_000L)
        )

    private fun describe(e: Throwable): String = e.message?.lineSequence()?.firstOrNull()?.take(120)
        ?: e::class.simpleName ?: "Unknown error"

    companion object {
        private const val TAG = "AiScheduleExtractor"
        const val MODEL = "gemini-3.5-flash-lite"
        private const val LOCATION = "global"
        private const val CHUNK_SIZE = 40
        private val START_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        private val nullableString = Schema.string(null, true)

        // Keys are in the order the model should fill them: evidence and season before the age.
        private val RESPONSE_SCHEMA = Schema.array(
            Schema.obj(
                linkedMapOf(
                    "uid" to Schema.string(),
                    "role" to nullableString,
                    "gameNumber" to nullableString,
                    "homeTeam" to nullableString,
                    "awayTeam" to nullableString,
                    "field" to nullableString,
                    "ageEvidence" to nullableString,
                    "seasonEndYear" to Schema.integer(),
                    "birthYearUsed" to Schema.integer(null, true),
                    "ageGroup" to Schema.enumeration(AgeGroup.entries.map { it.name })
                )
            )
        )
    }
}
