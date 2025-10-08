// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * Represents a feature flag with an optional variant.
 *
 * Example usage:
 * ```kotlin
 * // Using the constructor
 * val flag1 = FeatureFlag("my_flag", "variant_a")
 * val flag2 = FeatureFlag("another_flag", null)
 *
 * // Using the convenience methods
 * val flag3 = FeatureFlag.of("simple_flag")
 * val flag4 = FeatureFlag.of("complex_flag", "variant_b")
 *
 * // Setting multiple flags at once
 * logger.setFeatureFlags(listOf(flag1, flag2, flag3, flag4))
 * ```
 *
 * @param flag the name of the feature flag
 * @param variant the optional variant value
 */
data class FeatureFlag(
    private val flag: String,
    private val variant: String?,
) {
    /**
     * Gets the flag name.
     * This method is called by the JNI layer.
     */
    fun getFlag(): String = flag

    /**
     * Gets the variant value.
     * This method is called by the JNI layer.
     * @return the variant value, or null if not set
     */
    fun getVariant(): String? = variant

    /** Constructors */
    companion object {
        /**
         * Creates a feature flag with just a flag name (no variant).
         */
        @JvmStatic
        fun of(flag: String): FeatureFlag = FeatureFlag(flag, null)

        /**
         * Creates a feature flag with a flag name and variant.
         */
        @JvmStatic
        fun of(
            flag: String,
            variant: String?,
        ): FeatureFlag = FeatureFlag(flag, variant)
    }
}
