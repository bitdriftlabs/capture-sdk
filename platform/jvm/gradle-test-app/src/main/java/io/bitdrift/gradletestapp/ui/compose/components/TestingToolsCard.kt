// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.bitdrift.capture.LogLevel
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppExitReason
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.ui.designsystem.BdDropdownField
import io.bitdrift.gradletestapp.ui.designsystem.BdPrimaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard

/**
 * Testing Tools Card component
 */
@Composable
fun TestingToolsCard(
    uiState: AppState,
    onLogLevelChange: (LogLevel) -> Unit,
    onLogSingleMessage: () -> Unit,
    onLogManyMessages: () -> Unit,
    onLogJsonField: () -> Unit,
    onTestPreInitOrdering: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = stringResource(id = R.string.testing_tools),
        modifier = modifier,
    ) {
        BdDropdownField(
            label = "Log Level",
            selected = uiState.config.selectedLogLevel,
            options = LogLevel.entries,
            onSelected = onLogLevelChange,
            optionLabel = { it.name },
        )

        BdPrimaryButton(
            text = "Log Single Message",
            onClick = onLogSingleMessage,
            modifier = Modifier.fillMaxWidth(),
        )

        BdSecondaryButton(
            text = "Log Many Messages",
            onClick = onLogManyMessages,
            modifier = Modifier.fillMaxWidth(),
        )

        BdSecondaryButton(
            text = "Log JSON Field",
            onClick = onLogJsonField,
            modifier = Modifier.fillMaxWidth(),
        )
        BdSecondaryButton(
            text = "Test Ordering During Startup",
            onClick = onTestPreInitOrdering,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AppExitReasonSelector(
    selectedAppExitReason: AppExitReason,
    onAppExitReasonChange: (AppExitReason) -> Unit,
    modifier: Modifier = Modifier,
) {
    BdDropdownField(
        label = "Exit Reason",
        selected = selectedAppExitReason,
        options = AppExitReason.entries,
        onSelected = onAppExitReasonChange,
        modifier = modifier,
        optionLabel = { it.name },
    )
}
