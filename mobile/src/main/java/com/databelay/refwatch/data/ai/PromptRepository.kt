package com.databelay.refwatch.data.ai

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The schedule-extraction prompt the user edits in Settings, plus prompts they saved by name.
 * An unedited prompt is not stored, so users who never touch it pick up a new default with
 * each app update.
 */
@Singleton
class PromptRepository @Inject constructor(
    private val prefs: SharedPreferences
) {
    private val _activePrompt = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_PROMPT, null) ?: DEFAULT_EXTRACTION_PROMPT
    )
    val activePrompt: StateFlow<String> = _activePrompt.asStateFlow()

    private val _savedPrompts = MutableStateFlow(readSaved())
    /** Saved prompts by name, sorted by name. */
    val savedPrompts: StateFlow<Map<String, String>> = _savedPrompts.asStateFlow()

    fun setActivePrompt(text: String) {
        prefs.edit().apply {
            if (text == DEFAULT_EXTRACTION_PROMPT) remove(KEY_ACTIVE_PROMPT) else putString(KEY_ACTIVE_PROMPT, text)
        }.apply()
        _activePrompt.value = text
    }

    fun loadDefault() = setActivePrompt(DEFAULT_EXTRACTION_PROMPT)

    /** Makes the saved prompt [name] the active one. Returns false if there is no such prompt. */
    fun loadSaved(name: String): Boolean {
        val text = _savedPrompts.value[name] ?: return false
        setActivePrompt(text)
        return true
    }

    /** Saves [text] as [name], replacing any prompt already saved under that name. */
    fun save(name: String, text: String) = writeSaved(_savedPrompts.value + (name.trim() to text))

    fun delete(name: String) = writeSaved(_savedPrompts.value - name)

    private fun writeSaved(prompts: Map<String, String>) {
        val sorted = prompts.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        prefs.edit().putString(KEY_SAVED_PROMPTS, Json.encodeToString<Map<String, String>>(sorted.toMap())).apply()
        _savedPrompts.value = sorted
    }

    private fun readSaved(): Map<String, String> {
        val raw = prefs.getString(KEY_SAVED_PROMPTS, null) ?: return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, String>>(raw) }
            .getOrDefault(emptyMap())
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    companion object {
        private const val KEY_ACTIVE_PROMPT = "extraction_prompt_active"
        private const val KEY_SAVED_PROMPTS = "extraction_prompts_saved"
    }
}
