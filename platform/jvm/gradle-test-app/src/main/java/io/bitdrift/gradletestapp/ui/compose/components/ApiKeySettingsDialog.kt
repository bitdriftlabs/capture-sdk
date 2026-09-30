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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import io.bitdrift.gradletestapp.ui.designsystem.BdAlertDialog
import io.bitdrift.gradletestapp.ui.designsystem.bdFieldColors
import io.bitdrift.gradletestapp.ui.theme.BdShape
import io.bitdrift.gradletestapp.ui.theme.BitdriftTheme

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
                BitdriftTheme {
                    ApiKeysDialog(
                        onDismiss = { dismiss() },
                        sharedPreferences = sharedPreferences,
                    )
                }
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
        var firebaseApiKey by remember { getCurrentApiKeyValue(FIREBASE_API_KEY) }
        var firebaseAppId by remember { getCurrentApiKeyValue(FIREBASE_APP_ID) }
        var firebaseProjectId by remember { getCurrentApiKeyValue(FIREBASE_PROJECT_ID) }
        var firebaseSenderId by remember { getCurrentApiKeyValue(FIREBASE_SENDER_ID) }

        fun persistApiKeysAndDismiss() {
            sharedPreferences.edit {
                putString(BUG_SNAG_SDK_API_KEY, bugSnagSdkApiKey.text.trim())
                putString(SENTRY_SDK_DSN_KEY, sentrySdkDsnKey.text.trim())
                putString(FIREBASE_API_KEY, firebaseApiKey.text.trim())
                putString(FIREBASE_APP_ID, firebaseAppId.text.trim())
                putString(FIREBASE_PROJECT_ID, firebaseProjectId.text.trim())
                putString(FIREBASE_SENDER_ID, firebaseSenderId.text.trim())
            }
            onDismiss()
        }

        BdAlertDialog(
            title = "Enter API Keys",
            onDismiss = onDismiss,
            onConfirm = { persistApiKeysAndDismiss() },
        ) {
            ApiKeyTextField(
                value = bugSnagSdkApiKey,
                onValueChange = { bugSnagSdkApiKey = it },
                label = "Bugsnag API key",
                imeAction = ImeAction.Next,
            )
            ApiKeyTextField(
                value = sentrySdkDsnKey,
                onValueChange = { sentrySdkDsnKey = it },
                label = "Sentry DSN API key",
                imeAction = ImeAction.Next,
            )
            ApiKeyTextField(
                value = firebaseApiKey,
                onValueChange = { firebaseApiKey = it },
                label = "Firebase API key",
                imeAction = ImeAction.Next,
            )
            ApiKeyTextField(
                value = firebaseAppId,
                onValueChange = { firebaseAppId = it },
                label = "Firebase App ID",
                imeAction = ImeAction.Next,
            )
            ApiKeyTextField(
                value = firebaseProjectId,
                onValueChange = { firebaseProjectId = it },
                label = "Firebase Project ID",
                imeAction = ImeAction.Next,
            )
            ApiKeyTextField(
                value = firebaseSenderId,
                onValueChange = { firebaseSenderId = it },
                label = "Firebase Sender ID",
                imeAction = ImeAction.Done,
                onDoneAction = { persistApiKeysAndDismiss() },
            )
        }
    }

    @Composable
    private fun ApiKeyTextField(
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        label: String,
        imeAction: ImeAction,
        onDoneAction: (() -> Unit)? = null,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = BdShape.Field,
            colors = bdFieldColors(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDoneAction?.invoke() }),
        )
    }

    companion object {
        const val BUG_SNAG_SDK_API_KEY = "bugsnag_sdk_api_key"
        const val SENTRY_SDK_DSN_KEY = "sentry_sdk_dsn_key"
        const val FIREBASE_API_KEY = "firebase_api_key"
        const val FIREBASE_APP_ID = "firebase_app_id"
        const val FIREBASE_PROJECT_ID = "firebase_project_id"
        const val FIREBASE_SENDER_ID = "firebase_sender_id"
    }
}
