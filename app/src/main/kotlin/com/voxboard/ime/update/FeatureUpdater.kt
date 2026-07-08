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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.voxboard.BuildConfig
import com.voxboard.app.FlorisAppActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Represents a downloadable feature pack from GitHub.
 */
data class FeaturePack(
    val id: String,
    val type: String,
    val name: String,
    val version: Int,
    val description: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val checksum: String,
    val applyPath: String,
    val requires: List<String>,
)

/**
 * Downloads and applies feature packs (themes, dictionaries, ML models, extensions)
 * from the VoxBoard GitHub releases.
 */
class FeatureUpdater(private val context: Context) {

    companion object {
        private const val GITHUB_REPO = "Predator04/VoxBoard"
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/$GITHUB_REPO/main/feature-manifest.json"
        private const val USER_AGENT = "VoxBoard-FeatureUpdater"
        private const val PREFS_NAME = "voxboard_feature_prefs"
        private const val KEY_INSTALLED_FEATURES = "installed_features"
        private const val KEY_LAST_CHECK_MS = "features_last_check_ms"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // once per day

        private const val FEATURES_DIR = "features"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 30_000

        private const val NOTIFICATION_CHANNEL = "voxboard_features"
        private const val NOTIFICATION_ID_DOWNLOAD = 43
        private const val NOTIFICATION_ID_COMPLETE = 44
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val featuresDir = File(context.filesDir, FEATURES_DIR)

    /**
     * Check for new feature packs. Returns true if something was downloaded.
     */
    suspend fun checkForNewFeatures(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (now - lastCheck < CHECK_INTERVAL_MS) return@withContext false

        prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()

        try {
            val manifest = fetchManifest() ?: return@withContext false
            val installed = getInstalledFeatures()
            val appVersionCode = BuildConfig.VERSION_CODE
            var downloaded = false

            val features = manifest.optJSONArray("features") ?: return@withContext false
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val id = feature.optString("id", "")
                val version = feature.optInt("version", 0)
                val type = feature.optString("type", "")
                val minVersion = extractMinVersion(feature)

                // Skip if already installed at this version
                if (installed.containsKey(id)) {
                    val installedVersion = installed[id] ?: 0
                    if (installedVersion >= version) continue
                }

                // Skip if app version is too old
                if (minVersion > appVersionCode) continue

                // Download and apply
                val pack = FeaturePack(
                    id = id,
                    type = type,
                    name = feature.optString("name", id),
                    version = version,
                    description = feature.optString("description", ""),
                    downloadUrl = feature.optString("download_url", ""),
                    sizeBytes = feature.optLong("size_bytes", 0L),
                    checksum = feature.optString("checksum", ""),
                    applyPath = feature.optString("apply_path", ""),
                    requires = emptyList(),
                )

                if (downloadAndApply(pack)) {
                    markInstalled(id, version)
                    downloaded = true
                }
            }

            if (downloaded) {
                showFeaturesNotification()
            }

            downloaded
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Install a specific feature pack by ID (manual trigger from settings).
     */
    suspend fun installFeatureById(featureId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val manifest = fetchManifest() ?: return@withContext false
            val features = manifest.optJSONArray("features") ?: return@withContext false
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                if (feature.optString("id", "") == featureId) {
                    val pack = FeaturePack(
                        id = featureId,
                        type = feature.optString("type", ""),
                        name = feature.optString("name", featureId),
                        version = feature.optInt("version", 0),
                        description = feature.optString("description", ""),
                        downloadUrl = feature.optString("download_url", ""),
                        sizeBytes = feature.optLong("size_bytes", 0L),
                        checksum = feature.optString("checksum", ""),
                        applyPath = feature.optString("apply_path", ""),
                        requires = emptyList(),
                    )
                    return@withContext downloadAndApply(pack)
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get a list of currently installed feature packs with their versions.
     */
    fun getInstalledFeatureList(): Map<String, Int> {
        return getInstalledFeatures()
    }

    /**
     * Fetch the remote feature manifest from GitHub.
     */
    private fun fetchManifest(): JSONObject? {
        val url = URL(MANIFEST_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        return try {
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                JSONObject(body)
            } else null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Download a feature pack and apply it to the right directory.
     */
    private fun downloadAndApply(pack: FeaturePack): Boolean {
        if (pack.downloadUrl.isBlank()) return false

        try {
            if (!featuresDir.exists()) featuresDir.mkdirs()

            val url = URL(pack.downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT

            if (conn.responseCode != 200) {
                conn.disconnect()
                return false
            }

            // Determine the target file path
            val targetFile = File(featuresDir, "${pack.id}.pack")
            if (targetFile.exists()) targetFile.delete()

            val inputStream = conn.inputStream
            val outputStream = FileOutputStream(targetFile)
            val buffer = ByteArray(8192)
            inputStream.use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            conn.disconnect()

            if (!targetFile.exists() || targetFile.length() == 0L) return false

            // Verify checksum if provided
            if (pack.checksum.isNotBlank()) {
                val actual = sha256(targetFile)
                if (actual != pack.checksum.uppercase()) {
                    targetFile.delete()
                    return false
                }
            }

            // Extract and apply based on type
            return applyFeaturePack(pack, targetFile)
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Extract and apply a downloaded feature pack based on its type.
     */
    private fun applyFeaturePack(pack: FeaturePack, downloadedFile: File): Boolean {
        return try {
            when (pack.type) {
                "theme" -> applyTheme(pack, downloadedFile)
                "dictionary" -> applyDictionary(pack, downloadedFile)
                "ml_model" -> applyMlModel(pack, downloadedFile)
                "quick_phrases" -> applyQuickPhrases(pack, downloadedFile)
                "extension" -> applyExtension(pack, downloadedFile)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun applyTheme(pack: FeaturePack, file: File): Boolean {
        val themesDir = File(context.filesDir, "themes")
        if (!themesDir.exists()) themesDir.mkdirs()
        val dest = File(themesDir, pack.applyPath.substringAfterLast("/"))
        file.copyTo(dest, overwrite = true)
        return dest.exists()
    }

    private fun applyDictionary(pack: FeaturePack, file: File): Boolean {
        val dictsDir = File(context.filesDir, "dictionaries")
        if (!dictsDir.exists()) dictsDir.mkdirs()
        val dest = File(dictsDir, pack.applyPath.substringAfterLast("/"))
        file.copyTo(dest, overwrite = true)
        return dest.exists()
    }

    private fun applyMlModel(pack: FeaturePack, file: File): Boolean {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val dest = File(modelsDir, pack.applyPath.substringAfterLast("/"))
        file.copyTo(dest, overwrite = true)
        return dest.exists()
    }

    private fun applyQuickPhrases(pack: FeaturePack, file: File): Boolean {
        val phrasesDir = File(context.filesDir, "phrases")
        if (!phrasesDir.exists()) phrasesDir.mkdirs()
        val dest = File(phrasesDir, pack.applyPath.substringAfterLast("/"))
        file.copyTo(dest, overwrite = true)
        return dest.exists()
    }

    private fun applyExtension(pack: FeaturePack, file: File): Boolean {
        val extensionsDir = File(context.filesDir, "extensions")
        if (!extensionsDir.exists()) extensionsDir.mkdirs()
        val dest = File(extensionsDir, pack.applyPath.substringAfterLast("/"))
        file.copyTo(dest, overwrite = true)
        return dest.exists()
    }

    /**
     * Show a notification that new features have been installed.
     */
    private fun showFeaturesNotification() {
        createNotificationChannel()

        val intent = Intent(context, FlorisAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_whats_new", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("New features downloaded")
            .setContentText("VoxBoard has new themes and dictionaries ready")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("VoxBoard has downloaded new features in the background.\nTap to see what's new."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "VoxBoard Feature Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when new features are downloaded"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun getInstalledFeatures(): MutableMap<String, Int> {
        val raw = prefs.getString(KEY_INSTALLED_FEATURES, "{}") ?: "{}"
        val map = mutableMapOf<String, Int>()
        try {
            val json = JSONObject(raw)
            for (key in json.keys()) {
                map[key] = json.getInt(key)
            }
        } catch (_: Exception) {}
        return map
    }

    private fun markInstalled(id: String, version: Int) {
        val installed = getInstalledFeatures()
        installed[id] = version
        val json = JSONObject(installed.toMap()).toString()
        prefs.edit().putString(KEY_INSTALLED_FEATURES, json).apply()
    }

    private fun extractMinVersion(feature: JSONObject): Int {
        val requires = feature.optJSONArray("requires") ?: return 0
        for (i in 0 until requires.length()) {
            val req = requires.optString(i, "")
            if (req.startsWith("app_version >= ")) {
                return req.removePrefix("app_version >= ").trim().toIntOrNull() ?: 0
            }
        }
        return 0
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }
}
