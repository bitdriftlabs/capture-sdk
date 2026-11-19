// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

/**
 * The list of view types supported by Bitdrift Replay
 * @param typeValue The numeric value of the type.
 */
sealed class ReplayType(
    val typeValue: Int,
) {
    /**
     * Represents a Replay text label
     */
    data object Label : ReplayType(0)

    /**
     * Represents a Replay button
     */
    object Button : ReplayType(1)

    /**
     * Represents a Replay text input
     */
    object TextInput : ReplayType(2)

    /**
     * Represents a Replay image
     */
    object Image : ReplayType(3)

    /**
     * Represents a Replay generic view
     */
    object View : ReplayType(4)

    /**
     * Represents a Replay background image
     */
    object BackgroundImage : ReplayType(5)

    /**
     * Represents a Replay switch with an ON value
     */
    object SwitchOn : ReplayType(6)

    /**
     * Represents a Replay switch with an OFF value
     */
    object SwitchOff : ReplayType(7)

    /**
     * Represents a Replay map
     */
    object Map : ReplayType(8)

    /**
     * Represents a Replay chevron
     */
    object Chevron : ReplayType(9)

    /**
     * Represents a Replay transparent view
     */
    object TransparentView : ReplayType(10)

    /**
     * Represents a Replay software keyboard
     */
    object Keyboard : ReplayType(11)

    /**
     * Represents a Replay web view
     */
    object WebView : ReplayType(12)

    /**
     * Represents a view that is not supported by Replay
     */
    object Ignore : ReplayType(254)

    override fun toString(): String = this.javaClass.simpleName
}
