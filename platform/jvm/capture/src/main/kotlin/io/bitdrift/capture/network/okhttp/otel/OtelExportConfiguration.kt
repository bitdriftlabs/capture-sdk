// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp.otel

import okhttp3.HttpUrl

/**
 * Configuration for exporting OpenTelemetry spans for traced network requests to an external
 * OTLP/HTTP endpoint.
 *
 * The endpoint and auth header value are runtime inputs supplied by the host app, not anything
 * hardcoded in the SDK — this is intended to work with any OTLP/HTTP-compatible backend, not one
 * specific vendor.
 *
 * @param endpoint The OTLP/HTTP traces endpoint, e.g. `http://10.0.2.2:4318/v1/traces` for a
 *                 local collector reached from an Android emulator.
 * @param authHeaderValue The value to send in the auth header on every export request.
 * @param authHeaderName The auth header name. Defaults to `authorization`.
 */
data class OtelExportConfiguration
    @JvmOverloads
    constructor(
        val endpoint: HttpUrl,
        val authHeaderValue: String,
        val authHeaderName: String = "authorization",
    )
