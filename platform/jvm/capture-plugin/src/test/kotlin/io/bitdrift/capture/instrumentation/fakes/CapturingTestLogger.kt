// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/**
 * Adapted from https://github.com/getsentry/sentry-android-gradle-plugin/tree/4.14.1
 *
 * MIT License
 *
 * Copyright (c) 2020 Sentry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bitdrift.capture.instrumentation.fakes

class CapturingTestLogger : BaseTestLogger() {
    override fun getName(): String = "SentryPluginTest"

    var capturedMessage: String? = null
    var capturedThrowable: Throwable? = null

    override fun error(
        msg: String,
        throwable: Throwable?,
    ) {
        capturedMessage = msg
        capturedThrowable = throwable
    }

    override fun warn(
        msg: String,
        throwable: Throwable?,
    ) {
        capturedMessage = msg
        capturedThrowable = throwable
    }

    override fun warn(msg: String) {
        capturedMessage = msg
    }

    override fun info(
        msg: String,
        throwable: Throwable?,
    ) {
        capturedMessage = msg
        capturedThrowable = throwable
    }

    override fun debug(
        msg: String,
        throwable: Throwable?,
    ) {
        capturedMessage = msg
        capturedThrowable = throwable
    }
}
