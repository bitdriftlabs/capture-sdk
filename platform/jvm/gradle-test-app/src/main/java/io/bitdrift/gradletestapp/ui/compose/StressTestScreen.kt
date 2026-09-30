// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose

import android.app.Activity
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.DiskPressureState
import io.bitdrift.gradletestapp.data.model.JankType
import io.bitdrift.gradletestapp.data.model.StressTestAction
import io.bitdrift.gradletestapp.data.model.StrictModeViolationType
import io.bitdrift.gradletestapp.ui.designsystem.BdButtonSize
import io.bitdrift.gradletestapp.ui.designsystem.BdDropdownField
import io.bitdrift.gradletestapp.ui.designsystem.BdPrimaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdTintedButton
import io.bitdrift.gradletestapp.ui.designsystem.bdFieldColors
import io.bitdrift.gradletestapp.ui.theme.BdShape
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@Composable
fun StressTestScreen(
    onAction: (AppAction) -> Unit,
    onNavigateBack: () -> Unit,
    diskPressure: DiskPressureState,
) {
    LaunchedEffect(Unit) {
        onAction(StressTestAction.RefreshDiskSpace)
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = BdSpacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(BdSpacing.cardGap),
        contentPadding = PaddingValues(vertical = BdSpacing.md),
    ) {
        item {
            MemoryPressureCard(onAction = onAction)
        }
        item {
            DiskPressureCard(
                diskPressure = diskPressure,
                onAction = onAction,
            )
        }
        item {
            ThreadCountCard(onAction = onAction)
        }
        item {
            JankyFramesCard(onAction = onAction)
        }
        item {
            StrictModeCard(onAction = onAction)
        }
        item {
            ScreenReplayCard(onAction = onAction)
        }
    }
}

@Composable
private fun CardHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = BitdriftColors.TextSecondary,
    )
}

@Composable
private fun DiskPressureCard(
    diskPressure: DiskPressureState,
    onAction: (AppAction) -> Unit,
) {
    val context = LocalContext.current
    val availableBytes =
        when (diskPressure) {
            is DiskPressureState.Ready -> diskPressure.availableBytes
            is DiskPressureState.Filling -> diskPressure.availableBytes
            is DiskPressureState.Failed -> diskPressure.availableBytes
            DiskPressureState.Loading -> null
            DiskPressureState.UnsupportedDevice -> null
        }

    BdSectionCard(
        title = "Disk Pressure",
    ) {
        CardHint(
            when {
                diskPressure == DiskPressureState.Loading ->
                    "Checking whether disk pressure testing is supported."
                diskPressure != DiskPressureState.UnsupportedDevice ->
                    "Enable Deferred SDK Start in Settings, fill storage, then manually start the " +
                        "SDK to test initialization under ENOSPC."
                else ->
                    "Unavailable. Disk pressure testing requires an Android emulator."
            },
        )

        availableBytes?.let {
            Text(
                text = "Available: ${Formatter.formatFileSize(context, it)}",
                style = MaterialTheme.typography.bodyMedium,
                color = BitdriftColors.TextPrimary,
            )
        }

        (diskPressure as? DiskPressureState.Failed)?.let { failure ->
            Text(
                text = "Disk filler error: ${failure.message}",
                style = MaterialTheme.typography.bodySmall,
                color = BitdriftColors.Error,
            )
        }

        BdTintedButton(
            text = if (diskPressure is DiskPressureState.Filling) "Filling Internal Storage" else "Fill Internal Storage",
            accent = BitdriftColors.Error,
            onClick = { onAction(StressTestAction.FillDiskSpace) },
            enabled = diskPressure is DiskPressureState.Ready || diskPressure is DiskPressureState.Failed,
            modifier = Modifier.fillMaxWidth(),
        )

        BdSecondaryButton(
            text = "Clear Disk Pressure",
            onClick = { onAction(StressTestAction.ClearDiskSpace) },
            enabled =
                diskPressure is DiskPressureState.Ready ||
                    diskPressure is DiskPressureState.Filling ||
                    diskPressure is DiskPressureState.Failed,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThreadCountCard(onAction: (AppAction) -> Unit) {
    var threadCountInput by remember { mutableStateOf("5000") }
    val threadCount = threadCountInput.toIntOrNull()
    val isValid = threadCount != null && threadCount > 0

    BdSectionCard(
        title = "Threads",
        subtitle = "Create an exact number of sleeping background threads.",
    ) {
        OutlinedTextField(
            value = threadCountInput,
            onValueChange = { value ->
                if (value.all(Char::isDigit)) {
                    threadCountInput = value
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Thread count") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = threadCountInput.isNotEmpty() && !isValid,
            shape = BdShape.Field,
            colors = bdFieldColors(),
        )

        BdPrimaryButton(
            text = "Create Threads",
            onClick = { threadCount?.let { onAction(StressTestAction.CreateThreads(it)) } },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MemoryPressureCard(onAction: (AppAction) -> Unit) {
    var selectedPercent by remember { mutableIntStateOf(95) }
    val percentOptions = listOf(50, 70, 80, 90, 95, 98, 100)

    BdSectionCard(title = stringResource(id = R.string.memory_pressure)) {
        BdDropdownField(
            label = "Target %",
            selected = selectedPercent,
            options = percentOptions,
            onSelected = { selectedPercent = it },
            optionLabel = { "$it%" },
        )

        BdPrimaryButton(
            text = "Increase to $selectedPercent% (static)",
            onClick = { onAction(StressTestAction.IncreaseMemoryPressure(selectedPercent)) },
            modifier = Modifier.fillMaxWidth(),
        )

        BdTintedButton(
            text = "GC-Induced ANR (98% + allocations)",
            accent = BitdriftColors.Error,
            onClick = { onAction(StressTestAction.TriggerMemoryPressureAnr) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun JankyFramesCard(onAction: (AppAction) -> Unit) {
    var selectedJankType by remember { mutableStateOf(JankType.SLOW) }

    BdSectionCard(
        title = stringResource(id = R.string.janky_frames),
        subtitle = "Blocks main thread with Thread.sleep",
    ) {
        BdDropdownField(
            label = "Jank Type",
            selected = selectedJankType,
            options = JankType.entries,
            onSelected = { selectedJankType = it },
            optionLabel = { it.displayName },
        )

        BdTintedButton(
            text = "Trigger ${selectedJankType.displayName}",
            accent = BitdriftColors.Warning,
            onClick = { onAction(StressTestAction.TriggerJankyFrames(selectedJankType)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrictModeCard(onAction: (AppAction) -> Unit) {
    BdSectionCard(
        title = stringResource(id = R.string.strict_mode),
        subtitle = "Trigger deterministic StrictMode violations that map to the reporter fallbacks",
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BdSpacing.sm),
        ) {
            StrictModeViolationType.entries.forEach { type ->
                BdSecondaryButton(
                    text = type.displayName,
                    onClick = { onAction(StressTestAction.TriggerStrictModeViolation(type)) },
                    size = BdButtonSize.Compact,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenReplayCard(onAction: (AppAction) -> Unit) {
    @Suppress("ContextCastToActivity")
    val activity = LocalContext.current as Activity

    BdSectionCard(
        title = stringResource(id = R.string.screen_capture),
        subtitle = "Stress replay with window mutations",
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BdSpacing.sm),
        ) {
            BdSecondaryButton(
                text = "Trigger",
                onClick = { onAction(StressTestAction.TriggerScreenReplayCapture(activity)) },
                size = BdButtonSize.Compact,
            )
        }
    }
}

@Preview
@Composable
fun StressTestScreenPreview() {
    StressTestScreen({}, {}, DiskPressureState.Ready(0))
}
