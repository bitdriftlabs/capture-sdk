// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package com.bdreactnative

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap

abstract class BdReactNativeSpec internal constructor(context: ReactApplicationContext):
  ReactContextBaseJavaModule(context) {

  abstract fun init(key: String, sessionStrategy: String, options: ReadableMap?)

  abstract fun getDeviceID(promise: Promise)

  abstract fun getSessionID(promise: Promise)

  abstract fun getSessionURL(promise: Promise)

  abstract fun log(level: Double, message: String, jsFields: ReadableMap?)

  abstract fun addField(key: String, value: String)

  abstract fun removeField(key: String)

  abstract fun logScreenView(screenView: String)

  abstract fun logAppLaunchTTI(ttiMs: Double)

  abstract fun reportJsError(
    errorName: String,
    message: String,
    stack: String,
    isFatal: Boolean,
    engine: String,
    libraryVersion: String,
  )

  abstract fun setFeatureFlagExposureString(name: String, variant: String)

  abstract fun setFeatureFlagExposureBool(name: String, variant: Boolean)
}
