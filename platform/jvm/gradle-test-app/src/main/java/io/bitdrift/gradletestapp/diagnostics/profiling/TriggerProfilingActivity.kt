// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.gradletestapp.diagnostics.profiling

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import io.bitdrift.gradletestapp.BuildConfig

class TriggerProfilingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.E2E_TRIGGER_PROFILING) { "Build with -Pe2eTriggerProfiling=true" }
        runScenario()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runScenario()
    }

    private fun runScenario() {
        val scenario = intent.getStringExtra("scenario") ?: "status"
        setContentView(TextView(this).apply { text = "Local trigger profiling experiment: $scenario" })
        TriggerProfilingExperiment.runScenario(applicationContext, scenario)
    }
}
