// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import okhttp3.Request

/**
 * Reads the request path templates from [headerName] headers and returns them as a
 * comma separate list.
 */
class HeaderBasedOkHttpRequestPathTemplateProvider(
    private val headerName: String = "x-capture-path-template",
) : OkHttpRequestPathTemplateProvider {
    override fun providePathTemplate(request: Request): String? {
        val pathTemplateHeaderValues = request.headers.values(headerName)
        return if (pathTemplateHeaderValues.isEmpty()) {
            null
        } else {
            pathTemplateHeaderValues.joinToString(",")
        }
    }
}
