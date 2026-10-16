// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.flutter_example

import android.os.Handler
import android.os.Looper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "io.bitdrift.flutter_example/crash")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "nativeCrash" -> {
                        result.success(null)
                        Handler(Looper.getMainLooper()).post {
                            throw RuntimeException("Test JVM crash from Flutter example")
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
