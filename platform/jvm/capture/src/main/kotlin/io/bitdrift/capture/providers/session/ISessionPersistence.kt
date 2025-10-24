// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.providers.session

/**
 * Handles persisting/retrieving the current/previous session Uuid
 */
internal interface ISessionPersistence {
    /**
     * Store the current session uuid
     */
    fun saveCurrentSessionId(sessionUuid: String)

    /**
     * Retrieves the previous session uuid (if any)
     */
    fun getPreviousSessionId(): String?
}
