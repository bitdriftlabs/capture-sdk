// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material3.ElevatedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import io.bitdrift.capture.Capture
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.replay.compose.CaptureModifier.captureIgnore
import timber.log.Timber

@Composable
fun SecondScreen() {
    val span = Capture.Logger.startSpan("ComposeScreen", LogLevel.INFO)
    Surface {
        val smallSpacing = Dp(value = 5f)
        val normalSpacing = Dp(value = 10f)
        Column(Modifier.padding(normalSpacing)) {
            val centerWithPaddingModifier = Modifier
                .padding(horizontal = smallSpacing)
                .align(Alignment.CenterHorizontally)
            Text(
                text = "Hello Compose",
                style = MaterialTheme.typography.h6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = Dp(value = 20f))
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "This is Text inside a Column",
                    color = MaterialTheme.colors.primaryVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = centerWithPaddingModifier.padding(top = normalSpacing)
                )
                Text(
                    text = "This is a separate Text component",
                    modifier = centerWithPaddingModifier.padding(bottom = normalSpacing)
                )
            }
            HtmlDescription(
                description = "HTML Content inside an AndroidView<br/><br/><a href=\"https://developer.android.com\">Android Developers</a>"
            )
            ElevatedButton(
                onClick = { Timber.w("Warning logged from Compose Screen") },
                modifier = centerWithPaddingModifier.padding(top = normalSpacing).captureIgnore(ignoreSubTree = false)
            ) {
                Text("Log-a-log (Warning)")
            }
        }
    }
    span?.end(SpanResult.SUCCESS)
}

@Composable
private fun HtmlDescription(description: String) {
    // Remembers the HTML formatted description. Re-executes on a new description
    val htmlDescription = remember(description) {
        HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    // Displays the TextView on the screen and updates with the HTML description when inflated
    // Updates to htmlDescription will make AndroidView recompose and update the text
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = {
            it.text = htmlDescription
        }
    )
}
