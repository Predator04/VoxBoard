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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxboard.FlorisImeService
import com.voxboard.lib.devtools.flogInfo
import kotlinx.coroutines.launch

/**
 * Floating panel that offers AI writing actions when text is selected.
 */
@Composable
fun WritingAssistantPanel(
    visible: Boolean,
    selectedText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    val assistant = remember {
        WritingAssistant()
    }

    AnimatedVisibility(
        visible = visible && !isProcessing,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = "AI Writing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    WritingActionButton(
                        label = "Rewrite",
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                statusText = "Rewriting..."
                                val result = assistant.processText(selectedText, WritingAction.REWRITE)
                                if (result != null) {
                                    FlorisImeService.currentInputConnection()?.let { ic ->
                                        assistant.commitResult(ic, selectedText, result)
                                    }
                                    flogInfo { "AI: Rewrote text" }
                                }
                                isProcessing = false
                                statusText = ""
                                onDismiss()
                            }
                        },
                    )
                    WritingActionButton(
                        label = "Summarize",
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                statusText = "Summarizing..."
                                val result = assistant.processText(selectedText, WritingAction.SUMMARIZE)
                                if (result != null) {
                                    FlorisImeService.currentInputConnection()?.let { ic ->
                                        assistant.commitResult(ic, selectedText, result)
                                    }
                                }
                                isProcessing = false
                                statusText = ""
                                onDismiss()
                            }
                        },
                    )
                    WritingActionButton(
                        label = "Fix Grammar",
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                statusText = "Fixing grammar..."
                                val result = assistant.processText(selectedText, WritingAction.FIX_GRAMMAR)
                                if (result != null) {
                                    FlorisImeService.currentInputConnection()?.let { ic ->
                                        assistant.commitResult(ic, selectedText, result)
                                    }
                                }
                                isProcessing = false
                                statusText = ""
                                onDismiss()
                            }
                        },
                    )
                    WritingActionButton(
                        label = "Translate",
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                statusText = "Translating..."
                                val result = assistant.processText(selectedText, WritingAction.TRANSLATE)
                                if (result != null) {
                                    FlorisImeService.currentInputConnection()?.let { ic ->
                                        assistant.commitResult(ic, selectedText, result)
                                    }
                                }
                                isProcessing = false
                                statusText = ""
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WritingActionButton(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            fontSize = 12.sp,
        )
    }
}
