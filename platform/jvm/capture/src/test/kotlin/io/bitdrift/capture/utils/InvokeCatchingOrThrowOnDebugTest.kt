// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.ContextHolder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class InvokeCatchingOrThrowOnDebugTest {
    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun illegalStateExceptionAtCallback_whenDebugBuild_shouldThrowDebugCustomerCallbackException() {
        setIsDebuggable(true)

        val callback: (String) -> Unit = { throw IllegalStateException("customer error") }

        assertThatThrownBy {
            callback.invokeCatchingOrThrowOnDebug("arg")
        }.isInstanceOf(DebugCustomerCallbackException::class.java)
            .hasCauseInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun illegalStateExceptionAtCallback_whenDebugBuild_shouldNotThrowDebugCustomerCallbackException() {
        setIsDebuggable(false)

        val callback: (String) -> Unit = { throw IllegalStateException("customer error") }

        callback.invokeCatchingOrThrowOnDebug("arg")
    }

    @Test
    fun callbackSuccess_whenDebugBuild_shouldNotThrowDebugCustomerCallbackException() {
        setIsDebuggable(true)

        var called = false
        val callback: (String) -> Unit = { called = true }

        callback.invokeCatchingOrThrowOnDebug("arg")

        assertThat(called).isTrue()
    }

    @Test
    fun debugCustomerCallbackException_whenReleaseBuild_shouldBeNull() {
        setIsDebuggable(false)

        val result =
            DebugCustomerCallbackException.createIfDebug(
                IllegalStateException("error"),
            )

        assertThat(result).isNull()
    }

    @Test
    fun debugCustomerCallbackException_whenDebugBuild_shouldNotBeNull() {
        setIsDebuggable(true)

        val result =
            DebugCustomerCallbackException.createIfDebug(
                IllegalStateException("error"),
            )

        assertThat(result).isNotNull()
    }
}
