// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SettingsDataStore(
    context: Context,
) : PreferenceDataStore() {

    private val appContext = context.applicationContext
    private val dataStore = appContext.settingsDataStore

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val preferencesState = MutableStateFlow(emptyPreferences())

    init {
        scope.launch {
            dataStore.data.collect { preferencesState.value = it }
        }
    }


    private fun getPreferences(): Preferences {
        return preferencesState.value
    }

    override fun putString(key: String, value: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (value != null) {
                    prefs[stringPreferencesKey(key)] = value
                } else {
                    prefs.remove(stringPreferencesKey(key))
                }
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? =
        getPreferences()[stringPreferencesKey(key)] ?: defValue

    override fun putInt(key: String, value: Int) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[intPreferencesKey(key)] = value
            }
        }
    }

    override fun getInt(key: String, defValue: Int): Int =
        getPreferences()[intPreferencesKey(key)] ?: defValue

    override fun putLong(key: String, value: Long) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[longPreferencesKey(key)] = value
            }
        }
    }

    override fun getLong(key: String, defValue: Long): Long =
        getPreferences()[longPreferencesKey(key)] ?: defValue

    override fun putFloat(key: String, value: Float) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[floatPreferencesKey(key)] = value
            }
        }
    }

    override fun getFloat(key: String, defValue: Float): Float =
        getPreferences()[floatPreferencesKey(key)] ?: defValue

    override fun putBoolean(key: String, value: Boolean) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[booleanPreferencesKey(key)] = value
            }
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        getPreferences()[booleanPreferencesKey(key)] ?: defValue

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (values != null) {
                    prefs[stringSetPreferencesKey(key)] = values
                } else {
                    prefs.remove(stringSetPreferencesKey(key))
                }
            }
        }
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        getPreferences()[stringSetPreferencesKey(key)]?.toMutableSet() ?: defValues

}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)
