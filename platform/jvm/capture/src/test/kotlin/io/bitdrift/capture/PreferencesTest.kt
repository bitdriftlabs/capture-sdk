package io.bitdrift.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class PreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: Preferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = Preferences(context)
        // Clear preferences before each test
        val sharedPrefs = context.getSharedPreferences("io.bitdrift.storage", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
    }

    @Test
    fun `getLong returns value when key exists`() {
        val key = "test_key_long"
        val expectedValue = 123L
        preferences.setLong(key, expectedValue)

        val result = preferences.getLong(key)

        assertThat(result).isEqualTo(expectedValue)
    }

    @Test
    fun `getLong returns null when key does not exist`() {
        val key = "non_existent_key"
        val result = preferences.getLong(key)

        assertThat(result).isNull()
    }

    @Test
    fun `setLong saves the value`() {
        val key = "test_key_long_save"
        val value = 456L

        preferences.setLong(key, value)
        val savedValue = preferences.getLong(key)

        assertThat(savedValue).isEqualTo(value)
    }

    @Test
    fun `getString returns value when key exists`() {
        val key = "test_key_string"
        val expectedValue = "test_value"
        preferences.setString(key, expectedValue, blocking = false)

        val result = preferences.getString(key)

        assertThat(result).isEqualTo(expectedValue)
    }

    @Test
    fun `getString returns null when key does not exist`() {
        val key = "non_existent_string_key"

        val result = preferences.getString(key)

        assertThat(result).isNull()
    }

    @Test
    fun `setString saves non-null value with blocking`() {
        val key = "test_key_string_blocking"
        val value = "test_value_blocking"

        preferences.setString(key, value, blocking = true)
        val savedValue = preferences.getString(key)

        assertThat(savedValue).isEqualTo(value)
    }

    @Test
    fun `setString saves non-null value without blocking`() {
        val key = "test_key_string_non_blocking"
        val value = "test_value_non_blocking"

        preferences.setString(key, value, blocking = false)

        val savedValue = preferences.getString(key)

        assertThat(savedValue).isEqualTo(value)
    }

    @Test
    fun `setString removes value when null with blocking`() {
        val key = "test_key_to_remove_blocking"
        preferences.setString(key, "some_value", blocking = true)

        preferences.setString(key, null, blocking = true)
        val savedValue = preferences.getString(key)

        assertThat(savedValue).isNull()
    }

    @Test
    fun `setString removes value when null without blocking`() {
        val key = "test_key_to_remove_non_blocking"
        preferences.setString(key, "some_value", blocking = false)

        preferences.setString(key, null, blocking = false)

        val savedValue = preferences.getString(key)

        assertThat(savedValue).isNull()
    }
}
