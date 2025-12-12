// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * Used to allow the logger to call back up into the platform layer to determine the current
 * group and timestamp.
 */
internal interface IMetadataProvider {
    /**
     * Returns the current ms since UTC epoch using the active DateProvider.
     */
    fun timestamp(): Long

    /**
     * Returns out of the box fields to be included with emitted logs. Out of the box fields
     * are fields that come from the SDK itself.
     */
    fun ootbFields(): InternalFields

    /**
     * Returns custom fields to be included with emitted logs. Custom fields are fields
     * that come from the SDK customers.
     */
    fun customFields(): InternalFields
}
