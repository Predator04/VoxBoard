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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.voxboard.app.FlorisAppActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Checks for APK updates and new feature packs in the background.
 * Shows notifications for both.
 */
class UpdateNotifier(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "voxboard_updates"
        private const val NOTIFICATION_ID_APK = 42
        private const val NOTIFICATION_ID_FEATURES = 44
        private const val PREFS_NAME = "voxboard_update_prefs"
        private const val KEY_LAST_APK_CHECK_MS = "last_apk_check_ms"
        private const val KEY_LAST_VERSION_SHOWN = "last_version_shown"
        private const val KEY_LAST_FEATURE_CHECK_MS = "last_feature_check_ms"
        private const val APK_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val FEATURE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check for APK updates and new feature packs.
     * Pass showWhatsNew=true when triggered by main activity launch.
     */
    fun checkAll(showWhatsNew: Boolean = false) {
        scope.launch {
            try {
                // Check for APK update
                checkApkUpdate()
            } catch (_: Exception) {}

            try {
                // Check for new feature packs
                checkFeaturePacks()
            } catch (_: Exception) {}
        }
    }

    private suspend fun checkApkUpdate() {
        val lastCheck = prefs.getLong(KEY_LAST_APK_CHECK_MS, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck < APK_CHECK_INTERVAL_MS) return

        prefs.edit().putLong(KEY_LAST_APK_CHECK_MS, now).apply()

        val checker = UpdateChecker(context)
        val update = checker.checkForUpdate() ?: return

        // Check if we already notified about this version
        val lastShown = prefs.getString(KEY_LAST_VERSION_SHOWN, "")
        if (update.tagName == lastShown) return
        prefs.edit().putString(KEY_LAST_VERSION_SHOWN, update.tagName).apply()

        showApkUpdateNotification(update)
    }

    private suspend fun checkFeaturePacks() {
        val lastCheck = prefs.getLong(KEY_LAST_FEATURE_CHECK_MS, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck < FEATURE_CHECK_INTERVAL_MS) return

        prefs.edit().putLong(KEY_LAST_FEATURE_CHECK_MS, now).apply()

        val featureUpdater = FeatureUpdater(context)
        featureUpdater.checkForNewFeatures()
    }

    private fun showApkUpdateNotification(update: UpdateInfo) {
        createChannel()

        val intent = Intent(context, FlorisAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_update_dialog", update.tagName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("VoxBoard update available")
            .setContentText("v${update.versionName} is ready to download")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Version ${update.versionName} (${update.publishedAt}) is available.\nTap to update.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_APK, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VoxBoard Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when a new version of VoxBoard is available"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
