package io.bitdrift.capture.replay.internal.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

object CaptureModifier {

    val CaptureIgnore = SemanticsPropertyKey<Boolean>(
        name = "CaptureIgnoreModifier",
        mergePolicy = { parentValue, _ ->
            parentValue
        }
    )

    @JvmStatic
    fun Modifier.captureIgnore(ignoreSubTree: Boolean = false): Modifier {
        return semantics(
            properties = {
                this[CaptureIgnore] = ignoreSubTree
            }
        )
    }
}