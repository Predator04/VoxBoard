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

package com.voxboard.ime.writing

import android.view.inputmethod.InputConnection
import com.voxboard.lib.devtools.flogError
import com.voxboard.lib.devtools.flogInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles AI-powered writing assistance: rewrite, summarize, translate, fix grammar.
 *
 * Currently uses a configurable API endpoint. Future: replace with on-device model.
 */
enum class WritingAction(val displayName: String, val systemPrompt: String) {
    REWRITE("Rewrite", "Rewrite the following text to be clearer and more natural. Return ONLY the rewritten text, no explanations."),
    SUMMARIZE("Summarize", "Summarize the following text concisely. Return ONLY the summary, no explanations."),
    FIX_GRAMMAR("Fix Grammar", "Fix any grammar, spelling, and punctuation errors in the following text. Return ONLY the corrected text, no explanations."),
    TRANSLATE("Translate", "Translate the following text to English. If already in English, improve its clarity. Return ONLY the translated text, no explanations."),
}

class WritingAssistant(
    private val apiEndpoint: String = "https://api.openai.com/v1/chat/completions",
    private val apiKey: String = "",
    private val model: String = "gpt-4o-mini",
) {
    private var currentInputConnection: InputConnection? = null

    /**
     * Process selected text with the given action.
     * Returns the processed text or null on failure.
     */
    suspend fun processText(text: String, action: WritingAction): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isEmpty()) {
                    return@withContext fallbackProcess(text, action)
                }
                callAI(text, action)
            } catch (e: Exception) {
                flogError { "WritingAssistant error: ${e.message}" }
                fallbackProcess(text, action)
            }
        }
    }

    /**
     * Commit text to the current input field, replacing the selected text.
     */
    fun commitResult(connection: InputConnection?, originalText: String, result: String) {
        currentInputConnection = connection ?: return
        // Delete selected text and insert result
        connection?.beginBatchEdit()
        connection?.commitText(result, 1)
        connection?.endBatchEdit()
    }

    /**
     * Simple rule-based fallback when no API key is configured.
     * Provides basic functionality without any network calls.
     */
    private fun fallbackProcess(text: String, action: WritingAction): String {
        return when (action) {
            WritingAction.REWRITE -> {
                // Basic cleanup: trim whitespace, capitalize first letter, ensure period
                val trimmed = text.trim()
                val capitalized = trimmed.replaceFirstChar { it.uppercase() }
                if (!capitalized.endsWith(".") && !capitalized.endsWith("!") && !capitalized.endsWith("?")) {
                    "$capitalized."
                } else {
                    capitalized
                }
            }
            WritingAction.SUMMARIZE -> {
                // Return first sentence if text is long, otherwise return as-is
                val trimmed = text.trim()
                if (trimmed.length > 120) {
                    val firstSentence = trimmed.split("""(?<=[.!?])\s+""".toRegex()).firstOrNull() ?: trimmed
                    if (firstSentence.length > 50) firstSentence.take(100) + "..." else firstSentence
                } else {
                    trimmed
                }
            }
            WritingAction.FIX_GRAMMAR -> {
                // Basic capitalization and spacing fixes
                text.trim().replaceFirstChar { it.uppercase() }
                    .replace("""\s+""".toRegex(), " ")
                    .replace(" ,".toRegex(), ",")
                    .replace(""" \.""".toRegex(), ".")
            }
            WritingAction.TRANSLATE -> text.trim() // No-op fallback
        }
    }

    /**
     * Call an OpenAI-compatible API endpoint.
     */
    private fun callAI(text: String, action: WritingAction): String? {
        val requestBody = buildString {
            append("""{
                |"model": "$model",
                |"messages": [
                |    {"role": "system", "content": "${action.systemPrompt}"},
                |    {"role": "user", "content": "${text.replace("\"", "\\\"").replace("\n", "\\n")}"}
                |],
                |"temperature": 0.3,
                |"max_tokens": 1024
            |}""".trimMargin())
        }

        val url = URL(apiEndpoint)
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                // Parse the response to extract the content
                val contentRegex = Regex("\"content\"\\s*:\\s*\"([^\"]+)\"")
                val match = contentRegex.find(responseText)
                match?.groupValues?.getOrNull(1)?.replace("\\n", "\n")?.trim()
            } else {
                flogError { "AI API returned $responseCode: ${connection.errorStream?.bufferedReader()?.readText()}" }
                null
            }
        } catch (e: Exception) {
            flogError { "AI API call failed: ${e.message}" }
            null
        } finally {
            connection.disconnect()
        }
    }
}
