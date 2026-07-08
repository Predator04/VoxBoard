/*
 * Copyright (C) 2026 The VoxBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voxboard.ime.phrase

import android.content.Context
import com.voxboard.lib.devtools.flogError
import com.voxboard.lib.devtools.flogInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PhraseEntry(
    val trigger: String,
    val expansion: String,
    val enabled: Boolean = true,
)

/**
 * Manages quick phrases / text expansion snippets.
 * Stores trigger->expansion pairs in a JSON file in the app's private storage.
 *
 * When the user types a trigger word followed by space/punctuation,
 * it can be replaced with the expansion text.
 */
class PhraseManager(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val phrasesFile: File
        get() = File(context.filesDir, "voxboard_phrases.json")

    private var cache: MutableList<PhraseEntry>? = null

    /**
     * Get all phrase entries.
     */
    fun getAll(): List<PhraseEntry> {
        if (cache == null) {
            load()
        }
        return cache?.toList() ?: emptyList()
    }

    /**
     * Get all enabled phrase entries.
     */
    fun getEnabled(): List<PhraseEntry> {
        return getAll().filter { it.enabled }
    }

    /**
     * Add or update a phrase entry.
     */
    fun add(trigger: String, expansion: String) {
        val entries = getAll().toMutableList()
        val existing = entries.indexOfFirst { it.trigger == trigger }
        val entry = PhraseEntry(
            trigger = trigger.lowercase().trim(),
            expansion = expansion.trim(),
            enabled = true,
        )
        if (existing >= 0) {
            entries[existing] = entry
        } else {
            entries.add(entry)
        }
        save(entries)
    }

    /**
     * Remove a phrase entry by trigger.
     */
    fun remove(trigger: String): Boolean {
        val entries = getAll().toMutableList()
        val removed = entries.removeAll { it.trigger == trigger.lowercase().trim() }
        if (removed) save(entries)
        return removed
    }

    /**
     * Toggle enabled state of a phrase.
     */
    fun toggle(trigger: String) {
        val entries = getAll().toMutableList()
        val idx = entries.indexOfFirst { it.trigger == trigger.lowercase().trim() }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(enabled = !entries[idx].enabled)
            save(entries)
        }
    }

    /**
     * Check if the last word in the input matches any trigger.
     * Returns the expansion if matched, null otherwise.
     */
    fun checkWord(word: String): String? {
        val cleaned = word.trim().lowercase().trimEnd(',', '.', '!', '?', ';', ':')
        return getEnabled().firstOrNull { it.trigger == cleaned }?.expansion
    }

    /**
     * Check if the input ends with a trigger word (followed by space/punctuation).
     * Returns the expansion with the trigger replaced, or null.
     */
    fun checkAndReplace(input: String): String? {
        val trimmed = input.trimEnd()
        val triggerCandidates = getEnabled().filter { it.trigger.isNotEmpty() }
            .sortedByDescending { it.trigger.length } // Longest match first

        for (phrase in triggerCandidates) {
            // Check if the text ends with "trigger " or "trigger, " etc.
            val pattern = Regex("""\b${phrase.trigger}[.,!?;:\s]*$""", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(trimmed)) {
                // Replace the matched trigger with the expansion
                val result = pattern.replace(trimmed) { matchResult ->
                    val trailing = matchResult.value.substring(phrase.trigger.length)
                    phrase.expansion + trailing
                }
                return result
            }
        }
        return null
    }

    /**
     * Get all unique triggers as a set of lowercase strings.
     */
    fun triggerSet(): Set<String> {
        return getEnabled().map { it.trigger.lowercase().trim() }.toSet()
    }

    private fun load() {
        try {
            val file = phrasesFile
            if (file.exists()) {
                val text = file.readText().trim()
                if (text.isNotEmpty()) {
                    val entries = json.decodeFromString<List<PhraseEntry>>(text)
                    cache = entries.toMutableList()
                    flogInfo { "Loaded ${entries.size} quick phrases" }
                    return
                }
            }
        } catch (e: Exception) {
            flogError { "Failed to load phrases: ${e.message}" }
        }
        cache = mutableListOf()
    }

    private fun save(entries: List<PhraseEntry>) {
        try {
            val text = json.encodeToString(entries)
            phrasesFile.writeText(text)
            cache = entries.toMutableList()
            flogInfo { "Saved ${entries.size} quick phrases" }
        } catch (e: Exception) {
            flogError { "Failed to save phrases: ${e.message}" }
        }
    }

    /**
     * Load default phrases if none exist.
     */
    fun loadDefaults() {
        if (getAll().isEmpty()) {
            val defaults = listOf(
                PhraseEntry("brb", "be right back"),
                PhraseEntry("idk", "I don't know"),
                PhraseEntry("omw", "on my way!"),
                PhraseEntry("ttyl", "talk to you later"),
                PhraseEntry("g2g", "got to go"),
                PhraseEntry("imo", "in my opinion"),
                PhraseEntry("lol", "haha"),
                PhraseEntry("omg", "oh my god"),
                PhraseEntry("afaik", "as far as I know"),
                PhraseEntry("btw", "by the way"),
                PhraseEntry("fyi", "for your information"),
                PhraseEntry("smh", "shaking my head"),
                PhraseEntry("nvm", "never mind"),
                PhraseEntry("wth", "what the heck"),
                PhraseEntry("myaddr", "123 Main Street, Anytown, USA"),
                PhraseEntry("myemail", "predator04@gmail.com"),
                PhraseEntry("myphone", "555-0123"),
            )
            save(defaults)
        }
    }
}
