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

package com.voxboard.ime.update

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL

/**
 * Shows "What's New" after an APK update or after new features are downloaded.
 * Fetches changelog from GitHub feature-manifest.json.
 */
class WhatsNewActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(this).apply {
            text = "What's New in VoxBoard"
            textSize = 24f
        }
        container.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = "Loading changelog..."
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }
        container.addView(subtitleView)

        val closeBtn = Button(this).apply {
            text = "Got it!"
            setOnClickListener { finish() }
            setPadding(0, 32, 0, 0)
        }
        container.addView(closeBtn)

        scrollView.addView(container)
        setContentView(scrollView)

        // Fetch and display changelog
        scope.launch {
            try {
                val changelog = fetchChangelog()
                runOnUiThread {
                    subtitleView.text = changelog ?: "Check GitHub for the latest changes!"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    subtitleView.text = "See GitHub for what's new!"
                }
            }
        }
    }

    private fun fetchChangelog(): String? {
        val url = URL("https://raw.githubusercontent.com/Predator04/VoxBoard/main/feature-manifest.json")
        val conn = url.openConnection()
        conn.setRequestProperty("User-Agent", "VoxBoard")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val body = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(body)
        val changelog = json.optJSONArray("changelog")
        if (changelog == null || changelog.length() == 0) return null

        val latest = changelog.getJSONObject(0)
        val version = latest.optString("version", "?")
        val title = latest.optString("title", "Updates")
        val features = latest.optJSONArray("features")

        val sb = StringBuilder()
        sb.appendLine("v$version — $title")
        sb.appendLine()
        if (features != null) {
            for (i in 0 until features.length()) {
                sb.appendLine("• ${features.getString(i)}")
            }
        }
        return sb.toString()
    }
}
