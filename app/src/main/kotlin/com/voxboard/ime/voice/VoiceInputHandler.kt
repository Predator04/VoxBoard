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

package com.voxboard.ime.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputConnection
import com.voxboard.lib.devtools.flogError
import com.voxboard.lib.devtools.flogInfo

/**
 * Handles on-device voice input using Android's built-in SpeechRecognizer API.
 * This works offline if the user has downloaded offline speech recognition data
 * for their language in system settings.
 *
 * Future: Replace this with Whisper.cpp native library for fully offline,
 * privacy-first voice input without any dependency on Google services.
 */
class VoiceInputHandler(
    private val appContext: android.content.Context,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private var currentInputConnection: InputConnection? = null
    private var recognitionCallback: RecognitionCallback? = null

    interface RecognitionCallback {
        fun onVoiceResult(text: String)
        fun onVoiceError(message: String)
        fun onVoiceListening()
        fun onVoiceStopped()
    }

    /**
     * Start voice recognition. Results will be committed to the given input connection.
     */
    fun startListening(inputConnection: InputConnection?, callback: RecognitionCallback? = null) {
        stopListening()

        currentInputConnection = inputConnection
        recognitionCallback = callback

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    flogInfo { "Voice: ready for speech" }
                    isListening = true
                    recognitionCallback?.onVoiceListening()
                }

                override fun onBeginningOfSpeech() {
                    flogInfo { "Voice: beginning of speech" }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Can be used for visual feedback (mic level animation)
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received
                }

                override fun onEndOfSpeech() {
                    flogInfo { "Voice: end of speech" }
                    isListening = false
                    recognitionCallback?.onVoiceStopped()
                }

                override fun onError(error: Int) {
                    isListening = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
                        else -> "Unknown error ($error)"
                    }
                    flogError { "Voice recognition error: $errorMsg" }
                    recognitionCallback?.onVoiceError(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        flogInfo { "Voice result: $text" }
                        commitText(text)
                        recognitionCallback?.onVoiceResult(text)
                    } else {
                        recognitionCallback?.onVoiceError("No recognition results")
                    }
                    recognitionCallback?.onVoiceStopped()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        flogInfo { "Voice partial: ${matches[0]}" }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Not used
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            flogError { "Voice: Failed to start listening: ${e.message}" }
            recognitionCallback?.onVoiceError("Failed to start: ${e.message}")
        }
    }

    /**
     * Stop voice recognition if active.
     */
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
        }
        isListening = false
        recognitionCallback?.onVoiceStopped()
    }

    /**
     * Cancel current recognition without committing results.
     */
    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
        currentInputConnection = null
        recognitionCallback?.onVoiceStopped()
    }

    /**
     * Check if currently listening for voice input.
     */
    fun isListening(): Boolean = isListening

    private fun commitText(text: String) {
        val connection = currentInputConnection ?: return
        if (text.isNotEmpty()) {
            connection.commitText(text, 1)
        }
    }

    /**
     * Clean up resources. Call when the service is destroyed.
     */
    fun destroy() {
        cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
