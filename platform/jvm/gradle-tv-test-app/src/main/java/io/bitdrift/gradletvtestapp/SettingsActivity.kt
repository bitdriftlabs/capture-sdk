// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var apiKeyInput: EditText
    private lateinit var apiUrlInput: EditText
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.settings_title)
        setContentView(buildContentView())
        populateInputs()
    }

    private fun buildContentView(): ScrollView {
        val spacing = resources.getDimensionPixelSize(R.dimen.screen_spacing)

        statusView = TextView(this).apply {
            textSize = 18f
        }

        apiKeyInput = inputField(R.string.api_key_hint, InputType.TYPE_CLASS_TEXT)
        apiUrlInput = inputField(
            R.string.api_url_hint,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        )

        val saveButton = actionButton(R.string.save_settings) {
            saveSettings()
        }

        val backButton = actionButton(R.string.back_to_main) {
            finish()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(spacing, spacing, spacing, spacing)
            addView(titleView(), fullWidthLayoutParams(spacing))
            addView(descriptionView(), fullWidthLayoutParams(spacing))
            addView(labelView(R.string.api_key_label), fullWidthLayoutParams(spacing / 2))
            addView(apiKeyInput, fullWidthLayoutParams(spacing))
            addView(labelView(R.string.api_url_label), fullWidthLayoutParams(spacing / 2))
            addView(apiUrlInput, fullWidthLayoutParams(spacing))
            addView(statusView, fullWidthLayoutParams(spacing))
            addView(saveButton, fullWidthLayoutParams(spacing))
            addView(backButton, fullWidthLayoutParams(0))
        }

        return ScrollView(this).apply {
            isFillViewport = true
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun populateInputs() {
        val settings = TvSettingsStore.load(this)
        apiKeyInput.setText(settings.apiKey)
        apiUrlInput.setText(settings.apiUrl.ifBlank { TvSettingsStore.DEFAULT_API_URL })
        statusView.text = getString(R.string.settings_help_text)
    }

    private fun saveSettings() {
        val apiKey = apiKeyInput.text.toString().trim()
        val apiUrl = apiUrlInput.text.toString().trim()

        TvSettingsStore.save(
            context = this,
            settings = TvSettings(apiKey = apiKey, apiUrl = apiUrl),
        )

        val started = (application as GradleTvTestApp).startCaptureIfConfigured()
        statusView.text = if (started) {
            getString(R.string.settings_saved_started)
        } else {
            getString(R.string.settings_saved_invalid)
        }
    }

    private fun titleView(): TextView = TextView(this).apply {
        text = getString(R.string.settings_title)
        textSize = 28f
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun descriptionView(): TextView = TextView(this).apply {
        text = getString(R.string.settings_description)
        textSize = 18f
    }

    private fun labelView(textRes: Int): TextView = TextView(this).apply {
        text = getString(textRes)
        textSize = 18f
    }

    private fun inputField(hintRes: Int, inputType: Int): EditText = EditText(this).apply {
        hint = getString(hintRes)
        setSingleLine()
        this.inputType = inputType
        textSize = 18f
        minHeight = resources.getDimensionPixelSize(R.dimen.input_height)
    }

    private fun actionButton(textRes: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = getString(textRes)
        isAllCaps = false
        textSize = 20f
        minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
        setOnClickListener { onClick() }
    }

    private fun fullWidthLayoutParams(bottomMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.bottomMargin = bottomMargin
        }
}
