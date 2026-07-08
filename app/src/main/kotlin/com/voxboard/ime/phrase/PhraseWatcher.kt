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
import android.view.inputmethod.InputConnection
import com.voxboard.lib.devtools.flogInfo

/**
 * Watches typed text and replaces trigger words with their expansions.
 *
 * Hooks into the text commitment flow by observing what gets committed
 * and checking if the last word matches a phrase trigger.
 */
class PhraseWatcher(private val context: Context) {
    private val phraseManager by lazy { PhraseManager(context) }

    /** Buffer of recently typed characters, used to detect trigger words. */
    private val charBuffer = StringBuilder()

    /** Maximum characters to track in the buffer. */
    private val maxBufferSize = 64

    /** Triggers that are currently being looked for. */
    private var triggers: Set<String> = emptySet()

    /** Whether auto-expand is enabled. */
    var enabled: Boolean = true

    /**
     * Initialize the watcher. Call once when the IME service starts.
     */
    fun initialize() {
        phraseManager.loadDefaults()
        triggers = phraseManager.triggerSet()
        charBuffer.clear()
        flogInfo { "PhraseWatcher initialized with ${triggers.size} triggers" }
    }

    /**
     * Called when a character is committed to the editor.
     * Returns true if the text was replaced (trigger matched), false otherwise.
     */
    fun onCharCommitted(char: String, inputConnection: InputConnection?): Boolean {
        if (!enabled || triggers.isEmpty() || char.isEmpty() || inputConnection == null) {
            charBuffer.append(char)
            trimBuffer()
            return false
        }

        val isSpaceOrPunctuation = char.any { it == ' ' || it == ',' || it == '.' || it == '!' || it == '?' || it == ';' || it == ':' || it == '\n' || it == '\t' }

        if (isSpaceOrPunctuation) {
            // Check if the buffer ends with a trigger word
            val bufferText = charBuffer.toString().trim()
            val words = bufferText.split(Regex("\\s+"))
            if (words.isNotEmpty()) {
                val lastWord = words.last().trimEnd(',', '.', '!', '?', ';', ':')
                val expansion = phraseManager.checkWord(lastWord)
                if (expansion != null) {
                    // Delete the trigger word + any preceding space, insert the expansion + current char
                    val deleteLen = lastWord.length + 1 // +1 for the space before it
                    inputConnection.beginBatchEdit()
                    // Move cursor back, delete the trigger word
                    inputConnection.deleteSurroundingText(deleteLen, 0)
                    inputConnection.commitText("$expansion$char", 1)
                    inputConnection.endBatchEdit()
                    // Clear buffer and set it to the expansion
                    charBuffer.clear()
                    charBuffer.append(expansion)
                    charBuffer.append(char.trim())
                    trimBuffer()
                    flogInfo { "Phrase: '$lastWord' -> '$expansion'" }
                    return true
                }
            }
        }

        charBuffer.append(char)
        trimBuffer()
        return false
    }

    /**
     * Called when text is committed directly (e.g., from clipboard paste or quick action).
     */
    fun onTextCommitted(text: String, inputConnection: InputConnection?) {
        if (!enabled || inputConnection == null) return

        // Check the last word of the committed text
        val lastWord = text.trim().split(Regex("\\s+")).lastOrNull()?.trimEnd(',', '.', '!', '?', ';', ':') ?: return
        val expansion = phraseManager.checkWord(lastWord)
        if (expansion != null) {
            // Replace the last occurrence of the trigger word in the committed text
            val replaced = text.replaceLast(lastWord, expansion)
            inputConnection.beginBatchEdit()
            // Delete what we just committed and re-commit with expansion
            inputConnection.deleteSurroundingText(text.length, 0)
            inputConnection.commitText(replaced, 1)
            inputConnection.endBatchEdit()
            charBuffer.clear()
            charBuffer.append(replaced)
            trimBuffer()
            flogInfo { "Phrase (text): '$lastWord' -> '$expansion'" }
        } else {
            charBuffer.append(text)
            trimBuffer()
        }
    }

    /**
     * Clear the buffer (e.g., when the editor changes).
     */
    fun reset() {
        charBuffer.clear()
    }

    /**
     * Refresh the trigger set (call after phrases are modified).
     */
    fun refreshTriggers() {
        triggers = phraseManager.triggerSet()
    }

    fun getManager(): PhraseManager = phraseManager

    private fun trimBuffer() {
        if (charBuffer.length > maxBufferSize) {
            val excess = charBuffer.length - maxBufferSize
            charBuffer.delete(0, excess)
        }
    }

    private fun String.replaceLast(old: String, new: String): String {
        val lastIndex = lastIndexOf(old, ignoreCase = true)
        if (lastIndex < 0) return this
        return substring(0, lastIndex) + new + substring(lastIndex + old.length)
    }
}
