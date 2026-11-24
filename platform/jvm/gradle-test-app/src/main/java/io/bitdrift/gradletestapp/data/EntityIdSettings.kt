// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data

import android.content.SharedPreferences
import androidx.core.content.edit
import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger

internal object EntityIdSettings {
    const val PREFS_KEY = "entityId"

    fun read(preferences: SharedPreferences): String? =
        preferences.getString(PREFS_KEY, null)?.takeUnless { it.isBlank() }

    @Synchronized
    fun save(preferences: SharedPreferences, entityId: String) {
        preferences.edit {
            if (entityId.isBlank()) remove(PREFS_KEY) else putString(PREFS_KEY, entityId)
        }
        Capture.logger()?.let { applySaved(preferences, it) }
    }

    // Serialize the startup read and apply with live edits, including on background startup.
    @Synchronized
    fun applySaved(preferences: SharedPreferences, logger: ILogger) {
        val entityId = read(preferences)
        if (entityId == null) logger.clearEntityId() else logger.setEntityId(entityId)
    }
}
