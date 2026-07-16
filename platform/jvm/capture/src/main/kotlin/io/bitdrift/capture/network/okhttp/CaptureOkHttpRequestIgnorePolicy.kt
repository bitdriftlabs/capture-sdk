// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import io.bitdrift.capture.CaptureRuntimeProvider
import io.bitdrift.capture.IRuntimeProvider
import io.bitdrift.capture.common.RuntimeStringConfig
import okhttp3.Request

internal interface CaptureOkHttpRequestIgnorePolicy {
    fun shouldIgnore(request: Request): Boolean
}

internal class RuntimeOkHttpRequestIgnorePolicy(
    private val runtimeProvider: IRuntimeProvider = CaptureRuntimeProvider,
) : CaptureOkHttpRequestIgnorePolicy {
    private val ignoredPaths by lazy { readRuntimeCsvEntries(RuntimeStringConfig.NETWORK_REQUEST_IGNORE_PATHS_CSV) }

    private val requiredHeaders by lazy {
        readRuntimeCsvEntries(RuntimeStringConfig.NETWORK_REQUEST_IGNORE_REQUIRED_HEADERS_CSV)
    }

    override fun shouldIgnore(request: Request): Boolean {
        val hasIgnoredPaths = ignoredPaths.isNotEmpty()
        val hasRequiredHeaders = requiredHeaders.isNotEmpty()
        if (!hasIgnoredPaths && !hasRequiredHeaders) {
            return false
        }

        val matchesIgnoredPath = request.url.encodedPath in ignoredPaths
        val matchesRequiredHeader = requiredHeaders.any { request.header(it) != null }

        return when {
            hasIgnoredPaths && hasRequiredHeaders -> matchesIgnoredPath && matchesRequiredHeader
            hasIgnoredPaths -> matchesIgnoredPath
            else -> matchesRequiredHeader
        }
    }

    private fun readRuntimeCsvEntries(config: RuntimeStringConfig): Set<String> =
        runtimeProvider
            .getRuntimeStringConfigValue(config)
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
}
