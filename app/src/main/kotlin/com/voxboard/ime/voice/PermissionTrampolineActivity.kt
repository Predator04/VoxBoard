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

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible trampoline activity that requests RECORD_AUDIO permission at runtime.
 *
 * IME services (Services) cannot directly call requestPermissions(), so this
 * lightweight invisible activity shows the system permission dialog. Once the
 * user responds, it finishes immediately. The user can then tap the mic button
 * again and the permission check will pass.
 */
class PermissionTrampolineActivity : Activity() {
    companion object {
        private const val REQ_RECORD_AUDIO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Launch the system permission dialog immediately
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQ_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Toast.makeText(this, "Mic permission granted! Tap the mic button again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Mic permission denied. Voice input won't work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        // Finish immediately — this activity is invisible, user goes back to their app
        finish()
    }
}
