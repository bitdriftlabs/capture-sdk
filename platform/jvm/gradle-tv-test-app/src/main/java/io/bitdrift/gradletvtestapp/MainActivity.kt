// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.bitdrift.capture.Capture

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var copySessionUrlButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.app_name)

        val content = buildContentView()
        setContentView(content)
        refreshStatus()

        Capture.Logger.logInfo { "Main screen opened" }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildContentView(): ScrollView {
        val spacing = resources.getDimensionPixelSize(R.dimen.screen_spacing)

        statusView = TextView(this).apply {
            textSize = 18f
        }

        val logInfoButton = actionButton(R.string.log_info) {
            Capture.Logger.logInfo(mapOf("screen" to "main")) { "TV remote log button pressed" }
            refreshStatus(getString(R.string.logged_info_message))
        }

        val logErrorButton = actionButton(R.string.log_error) {
            Capture.Logger.logError(mapOf("screen" to "main")) { "TV remote error button pressed" }
            refreshStatus(getString(R.string.logged_error_message))
        }

        copySessionUrlButton = actionButton(R.string.copy_session_url) {
            copySessionUrl()
        }

        val openDetailsButton = actionButton(R.string.open_details) {
            Capture.Logger.logInfo { "Opening details screen" }
            startActivity(Intent(this, DetailsActivity::class.java))
        }

        val openLayoutsExamplesButton = actionButton(R.string.open_layouts_examples) {
            Capture.Logger.logInfo { "Opening TV layout examples" }
            startActivity(Intent(this, LayoutsExamplesActivity::class.java))
        }

        val openSettingsButton = actionButton(R.string.open_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(spacing, spacing, spacing, spacing)
            addView(headerView())
            addView(statusView, fullWidthLayoutParams(spacing))
            addView(logInfoButton, fullWidthLayoutParams(spacing))
            addView(logErrorButton, fullWidthLayoutParams(spacing))
            addView(copySessionUrlButton, fullWidthLayoutParams(spacing))
            addView(openDetailsButton, fullWidthLayoutParams(spacing))
            addView(openLayoutsExamplesButton, fullWidthLayoutParams(spacing))
            addView(openSettingsButton, fullWidthLayoutParams(0))
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

    private fun headerView(): TextView = TextView(this).apply {
        text = getString(R.string.main_title)
        textSize = 28f
        gravity = Gravity.CENTER_HORIZONTAL
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

    private fun refreshStatus(message: String? = null) {
        val settings = TvSettingsStore.load(this)
        val baseStatus = if (settings.apiKey.isBlank() || settings.apiUrl.isBlank()) {
            getString(R.string.missing_config_message)
        } else {
            getString(
                R.string.session_status,
                Capture.Logger.sessionId ?: getString(R.string.session_pending),
                BuildConfig.VERSION_NAME,
            )
        }

        copySessionUrlButton.isEnabled = !Capture.Logger.sessionUrl.isNullOrBlank()

        statusView.text = if (message == null) baseStatus else "$message\n\n$baseStatus"
    }

    private fun copySessionUrl() {
        val sessionUrl = Capture.Logger.sessionUrl
        if (sessionUrl.isNullOrBlank()) {
            refreshStatus(getString(R.string.copy_session_url_unavailable))
            return
        }

        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("bitdrift_session_url", sessionUrl))
        refreshStatus(getString(R.string.copy_session_url_success))
    }
}
