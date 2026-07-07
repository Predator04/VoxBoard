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

package com.voxboard.app.settings.writing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voxboard.lib.compose.FlorisScreen
import org.voxboard.lib.compose.stringRes

@Composable
fun WritingAssistantScreen() = FlorisScreen {
    title = "AI Writing Assistant"

    content {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "AI Writing Assistant helps you rewrite, summarize, fix grammar, and translate text directly from the keyboard.",
            )
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How to use:",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("- Select text in any text field")
                    Text("- Tap the AI Writing button in the toolbar")
                    Text("- Choose: Rewrite, Summarize, Fix Grammar, or Translate")
                    Text("- The result replaces your selected text")

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Note: Without an API key, basic offline fallback mode is used. " +
                            "For full AI-powered results, configure an API key in settings.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // API key field (future: store securely)
                    var apiKey by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("OpenAI API Key (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* Save API key to encrypted prefs */ },
                        enabled = apiKey.isNotEmpty(),
                    ) {
                        Text("Save API Key")
                    }
                }
            }
        }
    }
}
