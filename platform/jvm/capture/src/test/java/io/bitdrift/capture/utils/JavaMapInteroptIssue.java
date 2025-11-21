// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a map to be consumed by FieldProvider.toFields extension method to replicate potential
 * interopt issue
 */
public class JavaMapInteroptIssue {

    /**
     * Create a map with a null value for interopt issue with kotlin
     *
     * For more context see BIT-5914
     *
     * @return Map
     */
    public static Map<String, String> buildMapWithNullableValue() {
        final IllegalStateException exception = new IllegalStateException();
        final HashMap<String, String> fields = new HashMap<>();
        fields.put("exception_message", exception.getMessage());
        return fields;
    }
}
