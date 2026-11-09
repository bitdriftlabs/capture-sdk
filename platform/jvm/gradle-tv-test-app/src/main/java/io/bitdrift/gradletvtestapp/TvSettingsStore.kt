// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.content.Context

data class TvSettings(
    val apiKey: String,
    val apiUrl: String,
)

object TvSettingsStore {
    private const val PREFS_NAME = "gradle_tv_test_app"
    private const val API_KEY_PREF = "api_key"
    private const val API_URL_PREF = "api_url"

    const val DEFAULT_API_URL = "https://api.bitdrift.io"

    fun load(context: Context): TvSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TvSettings(
            apiKey = prefs.getString(API_KEY_PREF, "").orEmpty(),
            apiUrl = prefs.getString(API_URL_PREF, DEFAULT_API_URL).orEmpty(),
        )
    }

    fun save(context: Context, settings: TvSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(API_KEY_PREF, settings.apiKey)
            .putString(API_URL_PREF, settings.apiUrl)
            .apply()
    }
}
