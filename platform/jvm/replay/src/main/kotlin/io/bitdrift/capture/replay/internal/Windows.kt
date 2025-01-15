// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/**
 * Adapted from https://github.com/square/curtains/releases/tag/v1.2.5
 *
 * Copyright 2021 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bitdrift.capture.replay.internal

import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import kotlin.LazyThreadSafetyMode.NONE

/**
 * If this view is part of the view hierarchy from a [android.app.Activity], [android.app.Dialog] or
 * [android.service.dreams.DreamService], then this returns the [android.view.Window] instance
 * associated to it. Otherwise, this returns null.
 *
 * Note: this property is called [phoneWindow] because the only implementation of [Window] is
 * the internal class android.view.PhoneWindow.
 */
internal val View.phoneWindow: Window?
    get() {
        return WindowSpy.pullWindow(rootView)
    }

internal object WindowSpy {
    /**
     * Originally, DecorView was an inner class of PhoneWindow. In the initial import in 2009,
     * PhoneWindow is in com.android.internal.policy.impl.PhoneWindow and that didn't change until
     * API 23.
     * In API 22: https://android.googlesource.com/platform/frameworks/base/+/android-5.1.1_r38/policy/src/com/android/internal/policy/impl/PhoneWindow.java
     * PhoneWindow was then moved to android.view and then again to com.android.internal.policy
     * https://android.googlesource.com/platform/frameworks/base/+/b10e33ff804a831c71be9303146cea892b9aeb5d
     * https://android.googlesource.com/platform/frameworks/base/+/6711f3b34c2ad9c622f56a08b81e313795fe7647
     * In API 23: https://android.googlesource.com/platform/frameworks/base/+/android-6.0.0_r1/core/java/com/android/internal/policy/PhoneWindow.java
     * Then DecorView moved out of PhoneWindow into its own class:
     * https://android.googlesource.com/platform/frameworks/base/+/8804af2b63b0584034f7ec7d4dc701d06e6a8754
     * In API 24: https://android.googlesource.com/platform/frameworks/base/+/android-7.0.0_r1/core/java/com/android/internal/policy/DecorView.java
     */
    private val decorViewClass by lazy(NONE) {
        val sdkInt = Build.VERSION.SDK_INT
        // TODO: we can only consider API 26
        val decorViewClassName =
            when {
                sdkInt >= 24 -> "com.android.internal.policy.DecorView"
                sdkInt == 23 -> "com.android.internal.policy.PhoneWindow\$DecorView"
                else -> "com.android.internal.policy.impl.PhoneWindow\$DecorView"
            }
        try {
            Class.forName(decorViewClassName)
        } catch (ignored: Throwable) {
            Log.d(
                "WindowSpy",
                "Unexpected exception loading $decorViewClassName on API $sdkInt",
                ignored,
            )
            null
        }
    }

    /**
     * See [decorViewClass] for the AOSP history of the DecorView class.
     * Between the latest API 23 release and the first API 24 release, DecorView first became a
     * static class:
     * https://android.googlesource.com/platform/frameworks/base/+/0daf2102a20d224edeb4ee45dd4ee91889ef3e0c
     * Then it was extracted into a separate class.
     *
     * Hence the change of window field name from "this$0" to "mWindow" on API 24+.
     */
    private val windowField by lazy(NONE) {
        decorViewClass?.let { decorViewClass ->
            val sdkInt = Build.VERSION.SDK_INT
            val fieldName = if (sdkInt >= 24) "mWindow" else "this$0"
            try {
                decorViewClass.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (ignored: NoSuchFieldException) {
                Log.d(
                    "WindowSpy",
                    "Unexpected exception retrieving $decorViewClass#$fieldName on API $sdkInt",
                    ignored,
                )
                null
            }
        }
    }

    fun pullWindow(maybeDecorView: View): Window? =
        decorViewClass?.let { decorViewClass ->
            if (decorViewClass.isInstance(maybeDecorView)) {
                windowField?.let { windowField ->
                    windowField[maybeDecorView] as Window
                }
            } else {
                null
            }
        }
}
