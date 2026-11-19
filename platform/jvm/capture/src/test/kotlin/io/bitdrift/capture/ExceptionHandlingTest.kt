// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import org.junit.Test

class ExceptionHandlingTest {
    @Test
    fun `native exception handling`() {
        CaptureJniLibrary.load()
        // This test verifies that we call ExceptionClear after a function is invoked via the
        // ObjectHandle API. If this is not happening, then the JVM will throw an exception upon the
        // JNI function completing.
        CaptureTestJniLibrary.runExceptionHandlingTest()
    }
}
