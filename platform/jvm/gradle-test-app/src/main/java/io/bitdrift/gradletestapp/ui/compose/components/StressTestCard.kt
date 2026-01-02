// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@Composable
fun StressTestCard(
    onNavigateToStressTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = BitdriftColors.BackgroundPaper,
            ),
        shape = MaterialTheme.shapes.medium,
        border =
            BorderStroke(
                width = 1.dp,
                color = BitdriftColors.Border.copy(alpha = 0.3f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.stress_test),
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            Text(
                text = "Memory pressure, leaks, janky frames, StrictMode violations",
                style = MaterialTheme.typography.bodySmall,
                color = BitdriftColors.TextSecondary,
            )

            Button(
                onClick = onNavigateToStressTest,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitdriftColors.Primary,
                        contentColor = Color.White,
                    ),
            ) {
                Text(stringResource(id = R.string.open_stress_test))
            }
        }
    }
}

