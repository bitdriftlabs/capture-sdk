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

abstract class BdReactNativeSpec internal constructor(context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {

  abstract fun init(key: String, options: ReadableMap?)

  abstract fun log(level: Double, message: String, jsFields: ReadableMap?)
}
