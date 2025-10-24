// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.providers.session

import io.bitdrift.capture.IPreferences

/**
 * Concrete implementation of [io.bitdrift.capture.providers.session.ISessionPersistence]
 */
internal class SessionPersistenceImpl(
    private val preferences: IPreferences,
) : ISessionPersistence {
    override fun saveCurrentSessionId(sessionUuid: String) {
        preferences.setString(SESSION_UUID, sessionUuid, true)
    }

    override fun getPreviousSessionId(): String? = preferences.getString(SESSION_UUID)

    private companion object {
        private const val SESSION_UUID = "session_uuid"
    }
}
