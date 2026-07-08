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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.voxboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Represents a release fetched from GitHub.
 */
data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val publishedAt: String,
)

/**
 * Checks for updates on GitHub and handles APK download/install.
 * Uses the GitHub Releases API (no auth required for public repos).
 */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val GITHUB_REPO = "Predator04/VoxBoard"
        private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val FILE_PROVIDER_AUTHORITY_TEMPLATE = "%s.provider.file"

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    private val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    private val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /**
     * Check GitHub for newer releases. Returns the latest that is newer
     * than the installed version, or null if already up-to-date.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_URL?per_page=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "VoxBoard-UpdateChecker")
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS

            try {
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val error = if (conn.errorStream != null) {
                        conn.errorStream.bufferedReader().readText()
                    } else "HTTP $responseCode"
                    throw Exception("GitHub API: $error")
                }

                val body = conn.inputStream.bufferedReader().readText()
                val releases = JSONArray(body)
                if (releases.length() == 0) return@withContext null

                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    if (release.optBoolean("draft", false)) continue

                    val tagName = release.optString("tag_name", "")
                    val versionName = tagName.removePrefix("v")
                        .removeSuffix("-debug").removeSuffix("-beta").removeSuffix("-release")
                    val publishedAt = release.optString("published_at", "").take(10)

                    val assets = release.optJSONArray("assets") ?: continue
                    var apkUrl: String? = null
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null)
                            if (apkUrl != null) break
                        }
                    }

                    if (apkUrl == null) continue

                    val remoteVersionCode = parseVersionCode(tagName)
                    if (remoteVersionCode <= currentVersionCode) continue

                    val bodyText = release.optString("body", "").take(500)

                    return@withContext UpdateInfo(
                        versionName = versionName,
                        tagName = tagName,
                        downloadUrl = apkUrl,
                        releaseNotes = bodyText,
                        publishedAt = publishedAt,
                    )
                }

                null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            throw Exception("Check failed: ${e.message}")
        }
    }

    /**
     * Parse a version code from a tag like "v0.5.4-debug".
     * Format: major*10000 + minor*100 + patch.
     */
    private fun parseVersionCode(tag: String): Int {
        val cleaned = tag.removePrefix("v").substringBefore("-").trim()
        val parts = cleaned.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) return 0
        return parts[0] * 10_000 + parts[1] * 100 + parts[2]
    }

    /**
     * Download the APK and trigger the system package installer.
     * Returns true on success, throws on failure.
     * Opens browser as fallback if download fails.
     */
    suspend fun downloadAndInstall(update: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        // Step 1: Download the APK
        val apkFile: File
        try {
            apkFile = downloadApk(update)
        } catch (e: Exception) {
            // Download failed — open browser as fallback
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) { }
            throw Exception("Download: ${e.message} (opening browser)")
        }

        // Step 2: Trigger install via system package installer
        try {
            installApk(apkFile)
            true
        } catch (e: Exception) {
            // Install failed — try opening browser as final fallback
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) { }
            throw Exception("Install: ${e.message}")
        }
    }

    /**
     * Download APK from GitHub and save to app's private files.
     */
    private fun downloadApk(update: UpdateInfo): File {
        val url = URL(update.downloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "VoxBoard-UpdateChecker")
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS

        try {
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val error = if (conn.errorStream != null) {
                    conn.errorStream.bufferedReader().readText()
                } else "HTTP $responseCode"
                throw Exception("HTTP $responseCode — $error")
            }

            // Use app's cache directory for download (no external storage permissions needed)
            val downloadDir = File(context.cacheDir, "updates")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val apkFile = File(downloadDir, "VoxBoard-${update.tagName}.apk")
            // Delete any previous download
            if (apkFile.exists()) apkFile.delete()

            val inputStream = conn.inputStream
            val outputStream = FileOutputStream(apkFile)
            var totalBytes = 0L
            val buffer = ByteArray(8192)
            inputStream.use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                }
            }

            if (!apkFile.exists() || apkFile.length() == 0L) {
                throw Exception("File is empty after download")
            }

            return apkFile
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Launch the system package installer for the APK file.
     */
    private fun installApk(apkFile: File) {
        val fileProviderAuthority = FILE_PROVIDER_AUTHORITY_TEMPLATE.format(context.packageName)

        // Get a content URI via FileProvider
        val apkUri: Uri = try {
            FileProvider.getUriForFile(context, fileProviderAuthority, apkFile)
        } catch (e: Exception) {
            throw Exception("Can't share APK: ${e.message}")
        }

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // ACTION_INSTALL_PACKAGE may not be supported on all devices
            // Fall back to ACTION_VIEW
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(fallbackIntent)
        }
    }
}
