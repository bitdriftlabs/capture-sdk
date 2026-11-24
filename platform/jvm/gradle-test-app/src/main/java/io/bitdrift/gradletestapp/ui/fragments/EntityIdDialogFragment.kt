// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.EntityIdSettings

class EntityIdDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val builder = MaterialAlertDialogBuilder(context)
        val content = layoutInflater.cloneInContext(builder.context).inflate(R.layout.dialog_entity_id, null)
        val input = content.findViewById<EditText>(android.R.id.edit)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        input.setText(EntityIdSettings.read(preferences))
        input.setSelection(input.length())
        return builder
            .setTitle(R.string.entity_id_title)
            .setView(content)
            .setPositiveButton(R.string.entity_id_save) { _, _ -> submit(input.text.toString()) }
            .setNeutralButton(R.string.entity_id_clear) { _, _ -> submit("") }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun submit(entityId: String) {
        parentFragmentManager.setFragmentResult(TAG, bundleOf(EntityIdSettings.PREFS_KEY to entityId))
    }

    companion object {
        internal const val TAG = "entity_id_dialog"
    }
}
