/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package com.voxboard.ime.nlp.latin

import android.content.Context
import com.voxboard.appContext
import com.voxboard.ime.core.Subtype
import com.voxboard.ime.editor.EditorContent
import com.voxboard.ime.nlp.SpellingProvider
import com.voxboard.ime.nlp.SpellingResult
import com.voxboard.ime.nlp.SuggestionCandidate
import com.voxboard.ime.nlp.SuggestionProvider
import com.voxboard.ime.nlp.WordSuggestionCandidate
import com.voxboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.voxboard.lib.android.readText
import org.voxboard.lib.kotlin.guardedByLock

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.voxboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()

    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.

        wordData.withLock { wordData ->
            if (wordData.isEmpty()) {
                // Here we use readText() because the test dictionary is a json dictionary
                val rawData = appContext.assets.readText("ime/dict/data.json")
                val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                wordData.putAll(jsonData)
            }
        }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        val lower = word.lowercase()
        // Quick check: if word is empty or a single char, it's probably fine
        if (lower.length <= 1) return SpellingResult.validWord()
        // Check if word exists in our dictionary
        val exists = wordData.withLock { it.containsKey(lower) }
        if (exists) return SpellingResult.validWord()
        // Find close suggestions via edit distance (Levenshtein)
        val suggestions = wordData.withLock { data ->
            data.entries
                .filter { (w, _) -> w.length in (lower.length - 2)..(lower.length + 2) }
                .map { (w, _) -> w to levenshteinDistance(lower, w) }
                .filter { (_, dist) -> dist <= 2 || (dist <= 3 && lower.length > 5) }
                .sortedBy { (_, dist) -> dist }
                .take(maxSuggestionCount.coerceIn(1, 20))
                .map { (w, _) -> w }
        }
        return if (suggestions.isEmpty()) {
            SpellingResult.validWord()
        } else {
            SpellingResult.typo(suggestions.toTypedArray())
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val currentWord = content.composingText.lowercase().trim()
        if (currentWord.isBlank()) return emptyList()
        val topCandidates = wordData.withLock { data ->
            // Check if current word is an exact misspelling — find closest match
            val exactExists = data.containsKey(currentWord)
            if (!exactExists && currentWord.length >= 3) {
                // The user typed something not in the dictionary.
                // Find the closest edit-distance match as autocorrect candidate.
                val closest = data.entries
                    .filter { (w, _) -> w.length in (currentWord.length - 2)..(currentWord.length + 2) }
                    .map { (w, freq) -> Triple(w, levenshteinDistance(currentWord, w), freq) }
                    .filter { (_, dist, _) -> dist <= 2 }
                    .sortedWith(compareBy<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
                    .firstOrNull()
                if (closest != null) {
                    val word = closest.first
                    listOf(
                        WordSuggestionCandidate(
                            text = word,
                            confidence = 0.95,
                            isEligibleForAutoCommit = true,
                            sourceProvider = this@LatinLanguageProvider,
                        )
                    )
                } else {
                    // Prefix completions as fallback
                    data.entries
                        .filter { (w, _) -> w.startsWith(currentWord) && w != currentWord }
                        .sortedByDescending { (_, freq) -> freq }
                        .take(maxCandidateCount.coerceIn(1, 10))
                        .map { (word, freq) ->
                            WordSuggestionCandidate(
                                text = word,
                                confidence = (freq / 255.0).coerceIn(0.0, 1.0),
                                isEligibleForAutoCommit = currentWord.length >= 2,
                                sourceProvider = this@LatinLanguageProvider,
                            )
                        }
                }
            } else {
                // Word is in dictionary — show prefix completions
                data.entries
                    .filter { (w, _) -> w.startsWith(currentWord) && w != currentWord }
                    .sortedByDescending { (_, freq) -> freq }
                    .take(maxCandidateCount.coerceIn(1, 10))
                    .map { (word, freq) ->
                        WordSuggestionCandidate(
                            text = word,
                            confidence = (freq / 255.0).coerceIn(0.0, 1.0),
                            isEligibleForAutoCommit = currentWord.length >= 2,
                            sourceProvider = this@LatinLanguageProvider,
                        )
                    }
            }
        }
        return topCandidates
    }

    /**
     * Compute Levenshtein edit distance between two strings.
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        var prevDiag: Int
        for (i in 1..a.length) {
            dp[0] = i
            prevDiag = i - 1
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val old = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,        // deletion
                    dp[j - 1] + 1,    // insertion
                    prevDiag + cost   // substitution
                )
                prevDiag = old
            }
        }
        return dp[b.length]
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }
}
