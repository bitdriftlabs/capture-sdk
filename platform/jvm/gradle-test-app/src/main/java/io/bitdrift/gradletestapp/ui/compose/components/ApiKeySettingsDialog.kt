// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.AlertDialog
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Button
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.ButtonDefaults
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Text
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.ImeAction

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.bitdrift.gradletestapp.R

class SettingsApiKeysDialogFragment(
    private val sharedPreferences: SharedPreferences,
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setContent {
                ApiKeysDialog(
                    onDismiss = { dismiss() },
                    sharedPreferences = sharedPreferences,
                )
            }
        }

    @Composable
    fun ApiKeysDialog(
        onDismiss: () -> Unit,
        sharedPreferences: SharedPreferences,
    ) {
        fun getCurrentApiKeyValue(key: String) = mutableStateOf(TextFieldValue(sharedPreferences.getString(key, "") ?: ""))

        var bugSnagSdkApiKey by remember { getCurrentApiKeyValue(BUG_SNAG_SDK_API_KEY) }
        var sentrySdkDsnKey by remember { getCurrentApiKeyValue(SENTRY_SDK_DSN_KEY) }
        val localSoftwareKeyboardController = LocalSoftwareKeyboardController.current

        fun persistApiKeysAndDismiss() {
            val bugSnagSdkApiKeyTrimmed = bugSnagSdkApiKey.text.trim()
            val sentrySdkApiKeyTrimmed = sentrySdkDsnKey.text.trim()
            with(sharedPreferences.edit()) {
                putString(BUG_SNAG_SDK_API_KEY, bugSnagSdkApiKeyTrimmed)
                putString(SENTRY_SDK_DSN_KEY, sentrySdkApiKeyTrimmed)
                apply()
            }
            localSoftwareKeyboardController?.hide()
            onDismiss()
        }

        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = {
                Text(
                    text = "Enter API Keys",
                    fontSize = 20.sp,
                    modifier =
                        Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth(),
                )
            },
            text = {
                Column(
                    modifier =
                        Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    ApiKeyTextField(
                        value = bugSnagSdkApiKey,
                        onValueChange = { bugSnagSdkApiKey = it },
                        label = "Bugsnag API key",
                        onDoneAction = { persistApiKeysAndDismiss() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ApiKeyTextField(
                        value = sentrySdkDsnKey,
                        onValueChange = { sentrySdkDsnKey = it },
                        label = "Sentry DSN API key",
                        onDoneAction = { persistApiKeysAndDismiss() },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { persistApiKeysAndDismiss() },
                    colors =
                        ButtonDefaults.buttonColors(
                            backgroundColor = colorResource(id = R.color.green),
                        ),
                ) {
                    Text("OK")
                }
            },
        )
    }

    @Composable
    fun ApiKeyTextField(
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        label: String,
        onDoneAction: () -> Unit,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .wrapContentHeight(),
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        onDoneAction()
                    },
                ),
        )
    }

    companion object {
        const val BUG_SNAG_SDK_API_KEY = "bugsnag_sdk_api_key"
        const val SENTRY_SDK_DSN_KEY = "sentry_sdk_dsn_key"
    }
}
