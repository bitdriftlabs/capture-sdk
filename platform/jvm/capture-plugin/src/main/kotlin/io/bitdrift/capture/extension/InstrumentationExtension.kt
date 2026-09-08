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
         * Enables full automatic WebView instrumentation.
         *
         * @deprecated Use [webViewAutomaticInstrumentationMode] with
         * [WebViewAutomaticInstrumentationMode.ONLY_IF_JAVASCRIPT_ALREADY_ENABLED].
         */
        @Deprecated(
            message = "Use webViewAutomaticInstrumentationMode = ONLY_IF_JAVASCRIPT_ALREADY_ENABLED.",
            replaceWith = ReplaceWith("webViewAutomaticInstrumentationMode.set(ONLY_IF_JAVASCRIPT_ALREADY_ENABLED)"),
        )
        val automaticWebViewInstrumentation: Property<Boolean> =
            objects
                .property(Boolean::class.java)
                .convention(false)

        /**
         * Controls automatic WebView instrumentation via bytecode transformation.
         *
         * When unset, WebViews are not instrumented automatically. [WebViewAutomaticInstrumentationMode.FULL]
         * instruments all detected WebViews; [WebViewAutomaticInstrumentationMode.ONLY_IF_JAVASCRIPT_ALREADY_ENABLED]
         * instruments only WebViews whose application already enabled JavaScript.
         *
         * **Experimental:** This API may change in future releases.
         */
        val webViewAutomaticInstrumentationMode: Property<WebViewAutomaticInstrumentationMode> =
            objects.property(WebViewAutomaticInstrumentationMode::class.java)

        val debug: Property<Boolean> =
            objects.property(Boolean::class.java).convention(
                false,
            )

        val okHttpInstrumentationType: Property<OkHttpInstrumentationType> =
            objects
                .property(
                    OkHttpInstrumentationType::class.java,
                ).convention(OkHttpInstrumentationType.PROXY)

        enum class OkHttpInstrumentationType {
            PROXY,
            OVERWRITE,
        }

        enum class WebViewAutomaticInstrumentationMode {
            /** Instruments all detected WebViews and enables JavaScript when needed. */
            FULL,

            /** Instruments only WebViews whose application already enabled JavaScript. */
            ONLY_IF_JAVASCRIPT_ALREADY_ENABLED,
        }

        // Helpers so that these values can be used directly in the DSL
        val PROXY = OkHttpInstrumentationType.PROXY
        val OVERWRITE = OkHttpInstrumentationType.OVERWRITE
        val FULL = WebViewAutomaticInstrumentationMode.FULL
        val ONLY_IF_JAVASCRIPT_ALREADY_ENABLED =
            WebViewAutomaticInstrumentationMode.ONLY_IF_JAVASCRIPT_ALREADY_ENABLED
    }
