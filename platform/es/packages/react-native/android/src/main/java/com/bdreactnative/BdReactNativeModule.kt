// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package com.bdreactnative

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import io.bitdrift.capture.Capture
import io.bitdrift.capture.providers.session.SessionStrategy
import okhttp3.HttpUrl.Companion.toHttpUrl

class BdReactNativeModule internal constructor(context: ReactApplicationContext) :
  BdReactNativeSpec(context) {

  override fun getName(): String {
    return NAME
  }

  @ReactMethod
  override fun init(key: String, options: ReadableMap?) {
    val apiUrl = options?.getString("url") ?: "https://api.bitdrift.io"

    Capture.Logger.start(apiKey = key, apiUrl = apiUrl.toHttpUrl(), sessionStrategy = SessionStrategy.Fixed())
  }

  @ReactMethod
  override fun log(level: Double, message: String, jsFields: ReadableMap?) {
    val fields = jsFields?.toHashMap()?.mapValues { it.value.toString() } ?: emptyMap()

    when (level) {
      0.0 -> Capture.Logger.logTrace(fields) { message }
      1.0 -> Capture.Logger.logDebug(fields) { message }
      2.0 -> Capture.Logger.logInfo(fields) { message }
      3.0 -> Capture.Logger.logWarning(fields) { message }
      4.0 -> Capture.Logger.logError(fields) { message }
    }
  }

  companion object {
    const val NAME = "BdReactNative"
  }
}
