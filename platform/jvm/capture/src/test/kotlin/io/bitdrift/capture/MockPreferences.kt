// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

// Allowing to view internals only for tests
@Suppress("INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
class MockPreferences : IPreferences {
    private val stringStorage = HashMap<String, String>()
    private val longStorage = HashMap<String, Long>()

    override fun getLong(key: String): Long? {
        return longStorage[key]
    }

    override fun setLong(key: String, value: Long) {
        longStorage[key] = value
    }

    override fun getString(key: String): String? {
        return stringStorage[key]
    }

    override fun setString(key: String, value: String?, blocking: Boolean) {
        if (value != null) {
            stringStorage[key] = value
        } else {
            stringStorage.remove(key)
        }
    }
}
