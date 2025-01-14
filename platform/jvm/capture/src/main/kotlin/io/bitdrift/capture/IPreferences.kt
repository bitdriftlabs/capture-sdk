// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

internal interface IPreferences {
    /**
     * Returns a stored long value for a given key.
     *
     * @param key key to return the value for.
     */
    fun getLong(key: String): Long?

    /**
     * Stores a given value for a specified key.
     *
     * @param key key to set a given value for.
     * @param value value to be set.
     */
    fun setLong(
        key: String,
        value: Long,
    )

    /**
     * Returns a stored string value for a given key.
     *
     * @param key key to return the value for.
     */
    fun getString(key: String): String?

    /**
     * Stores a given value for a specified key.
     *
     * @param key key to set a given value for.
     * @param value value to be set, pass null to erase a value for a given key.
     * @param blocking whether the operation should return only after the changes are persisted to disk.
     */
    fun setString(
        key: String,
        value: String?,
        blocking: Boolean,
    )
}
