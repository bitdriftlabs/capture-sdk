// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.extension

import org.gradle.api.Project
import org.gradle.api.provider.Property
import javax.inject.Inject

open class InstrumentationExtension
    @Inject
    constructor(
        project: Project,
    ) {
        private val objects = project.objects

        val automaticOkHttpInstrumentation: Property<Boolean> =
            objects
                .property(Boolean::class.java)
                .convention(false)

        /**
         * Enables automatic WebView instrumentation via bytecode transformation.
         *
         * When enabled, the plugin injects calls to [io.bitdrift.capture.webview.WebViewCapture]
         * at WebView load sites. For instrumentation to be active at runtime, a valid
         * [io.bitdrift.capture.webview.WebViewConfiguration] must be provided when calling
         * `Capture.Logger.start()`. If `null` is passed, the injected calls will be no-op.
         *
         * **Experimental:** This API may change in future releases.
         */
        val automaticWebViewInstrumentation: Property<Boolean> =
            objects
                .property(Boolean::class.java)
                .convention(false)

        val debug: Property<Boolean> =
            objects.property(Boolean::class.java).convention(
                false,
            )

        val okHttpInstrumentationType: Property<OkHttpInstrumentationType> =
            objects
                .property(
                    OkHttpInstrumentationType::class.java,
                ).convention(OkHttpInstrumentationType.OVERWRITE)

        enum class OkHttpInstrumentationType {
            PROXY,
            OVERWRITE,
        }

        // Helpers so that these values can be used directly in the DSL
        val PROXY = OkHttpInstrumentationType.PROXY
        val OVERWRITE = OkHttpInstrumentationType.OVERWRITE
    }
