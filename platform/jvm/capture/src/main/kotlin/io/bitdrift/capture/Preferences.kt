// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE

/**
 * Convenience wrapper for working with `SharedPreferences`.
 */
internal class Preferences(private val context: Context) : IPreferences {
    private val underlyingPreferences = context.getSharedPreferences("io.bitdrift.storage", MODE_PRIVATE)

    override fun getLong(key: String): Long? {
        return if (underlyingPreferences.contains(key)) {
            underlyingPreferences.getLong(key, -1)
        } else {
            null
        }
    }

    override fun setLong(key: String, value: Long) {
        underlyingPreferences.edit().putLong(key, value).apply()
    }

    override fun getString(key: String): String? {
        return underlyingPreferences.getString(key, null)
    }

    @SuppressLint("ApplySharedPref")
    override fun setString(key: String, value: String?, blocking: Boolean) {
        val edit = if (value == null) {
            underlyingPreferences.edit().remove(key)
        } else {
            underlyingPreferences.edit().putString(key, value)
        }

        if (blocking) {
            edit.commit()
        } else {
            edit.apply()
        }
    }
}
