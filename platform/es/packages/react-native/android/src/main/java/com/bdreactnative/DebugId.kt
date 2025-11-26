// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package com.bdreactnative

import android.content.res.AssetManager
import android.util.Log
import java.security.MessageDigest

object DebugId {
    private const val TAG = "BdReactNative"
    private const val BUNDLE_FILE = "index.android.bundle"
    private const val BUFFER_SIZE = 8192

    /**
     * Generates a debug ID from the JavaScript bundle's MD5 hash.
     * Returns null if the bundle cannot be found or read.
     */
    fun fromBundle(assets: AssetManager): String? {
        return try {
            calculateBundleMD5(assets)
        } catch (e: Exception) {
            Log.e(TAG, "Could not generate debug ID from bundle", e)
            null
        }
    }

    private fun calculateBundleMD5(assets: AssetManager): String {
        val messageDigest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(BUFFER_SIZE)
        
        assets.open(BUNDLE_FILE).use { inputStream ->
            var bytesRead = inputStream.read(buffer)
            while (bytesRead != -1) {
                messageDigest.update(buffer, 0, bytesRead)
                bytesRead = inputStream.read(buffer)
            }
        }
        
        val hashBytes = messageDigest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
