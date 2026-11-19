// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object BdShape {
    val Field = RoundedCornerShape(8.dp)
    val Button = RoundedCornerShape(8.dp)
    val Card = RoundedCornerShape(8.dp)
    val Inner = RoundedCornerShape(8.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

internal val BdShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = BdShape.Field,
        medium = BdShape.Card,
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
