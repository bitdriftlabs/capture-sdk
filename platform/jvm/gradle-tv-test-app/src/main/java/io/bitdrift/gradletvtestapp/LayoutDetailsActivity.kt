// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class LayoutDetailsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, LayoutDetailsFragment())
                .commit()
        }
    }

    companion object {
        private const val EXTRA_EXAMPLE_ID = "example_id"

        fun intent(context: Context, exampleId: String): Intent =
            Intent(context, LayoutDetailsActivity::class.java)
                .putExtra(EXTRA_EXAMPLE_ID, exampleId)

        fun exampleId(intent: Intent): String? = intent.getStringExtra(EXTRA_EXAMPLE_ID)
    }
}
