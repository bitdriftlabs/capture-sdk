// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import okhttp3.Request

/**
 * Provides the path template for a given request as defined in
 * [io.bitdrift.capture.network.HttpUrlPath.template] for http logs automatically sent
 * by using [CaptureOkHttpEventListenerFactory].
 */
fun interface OkHttpRequestPathTemplateProvider {
    /**
     * @return The path template (i.e., "/path/<id>") for this request. If the template is not
     * specified, the SDK detects and replaces high-cardinality path portions with the "<id>"
     * placeholder.
     */
    fun providePathTemplate(request: Request): String?
}
