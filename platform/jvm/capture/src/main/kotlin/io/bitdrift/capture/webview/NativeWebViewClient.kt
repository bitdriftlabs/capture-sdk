// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.KeyEvent
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.fieldsOfOptional

/**
 * A WebViewClient wrapper that captures page load events and errors via native callbacks.
 * This is used in NATIVE_ONLY mode where JavaScript injection is not desired.
 *
 * All callbacks are delegated to the original WebViewClient if one was set.
 */
internal class NativeWebViewClient(
    private val originalClient: WebViewClient?,
    private val logger: IInternalLogger,
    private val config: WebViewConfiguration.NativeOnly,
) : WebViewClient() {
    private var pageStartTime: Long = 0L

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?,
    ) {
        pageStartTime = System.currentTimeMillis()
        if (config.capturePageViews) {
            logger.logInternal(
                LogType.VIEW,
                LogLevel.DEBUG,
                fieldsOf(
                    "_url" to url,
                    "_action" to "start",
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                "webview.pageView"
            }
        }
        originalClient?.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        if (config.capturePageViews) {
            val durationMs =
                if (pageStartTime > 0) {
                    System.currentTimeMillis() - pageStartTime
                } else {
                    null
                }
            val fields =
                fieldsOfOptional(
                    "_url" to url,
                    "_action" to "end",
                    "_source" to "webview",
                    "_mode" to "native",
                    "_duration_ms" to durationMs?.toString(),
                )
            logger.logInternal(LogType.VIEW, LogLevel.DEBUG, fields) {
                "webview.pageView"
            }
        }
        originalClient?.onPageFinished(view, url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (config.captureErrors) {
            val message = if (request.isForMainFrame) "webview.error" else "webview.resourceError"
            logger.log(
                LogLevel.ERROR,
                fieldsOf(
                    "_url" to request.url.toString(),
                    "_error_code" to error.errorCode.toString(),
                    "_description" to error.description.toString(),
                    "_is_for_main_frame" to request.isForMainFrame.toString(),
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                message
            }
        }
        originalClient?.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (config.captureErrors) {
            logger.log(
                LogLevel.WARNING,
                fieldsOf(
                    "_url" to request.url.toString(),
                    "_status_code" to errorResponse.statusCode.toString(),
                    "_reason_phrase" to (errorResponse.reasonPhrase ?: ""),
                    "_is_for_main_frame" to request.isForMainFrame.toString(),
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                "webview.httpError"
            }
        }
        originalClient?.onReceivedHttpError(view, request, errorResponse)
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        if (config.captureErrors) {
            logger.log(
                LogLevel.ERROR,
                fieldsOf(
                    "_url" to error.url,
                    "_primary_error" to error.primaryError.toString(),
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                "webview.sslError"
            }
        }
        originalClient?.onReceivedSslError(view, handler, error)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (config.captureNavigationEvents) {
            logger.logInternal(
                LogType.VIEW,
                LogLevel.DEBUG,
                fieldsOf(
                    "_fromUrl" to view.url.orEmpty(),
                    "_toUrl" to request.url.toString(),
                    "_method" to request.method,
                    "_is_redirect" to request.isRedirect.toString(),
                    "_is_for_main_frame" to request.isForMainFrame.toString(),
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                "webview.navigation"
            }
        }
        return originalClient?.shouldOverrideUrlLoading(view, request) ?: false
    }

    override fun doUpdateVisitedHistory(
        view: WebView,
        url: String,
        isReload: Boolean,
    ) {
        if (config.captureNavigationEvents) {
            logger.logInternal(
                LogType.VIEW,
                LogLevel.DEBUG,
                fieldsOf(
                    "_url" to url,
                    "_method" to if (isReload) "reload" else "navigate",
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                "webview.navigation"
            }
        }
        originalClient?.doUpdateVisitedHistory(view, url, isReload)
    }

    override fun onLoadResource(
        view: WebView,
        url: String,
    ) {
        if (config.captureResourceLoads) {
            logger.logInternal(
                LogType.VIEW,
                LogLevel.DEBUG,
                fieldsOf(
                    "_url" to url,
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                "webview.loadResource"
            }
        }
        originalClient?.onLoadResource(view, url)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        if (config.captureErrors) {
            logger.log(
                LogLevel.ERROR,
                fieldsOf(
                    "_did_crash" to detail.didCrash().toString(),
                    "_renderer_priority" to detail.rendererPriorityAtExit().toString(),
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                "webview.renderProcessGone"
            }
        }
        return originalClient?.onRenderProcessGone(view, detail) ?: false
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = originalClient?.shouldInterceptRequest(view, request)

    override fun onFormResubmission(
        view: WebView,
        dontResend: Message,
        resend: Message,
    ) {
        originalClient?.onFormResubmission(view, dontResend, resend)
    }

    override fun onReceivedClientCertRequest(
        view: WebView,
        request: ClientCertRequest,
    ) {
        originalClient?.onReceivedClientCertRequest(view, request)
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String,
    ) {
        originalClient?.onReceivedHttpAuthRequest(view, handler, host, realm)
    }

    override fun onReceivedLoginRequest(
        view: WebView,
        realm: String,
        account: String?,
        args: String,
    ) {
        originalClient?.onReceivedLoginRequest(view, realm, account, args)
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        originalClient?.onSafeBrowsingHit(view, request, threatType, callback)
    }

    override fun onScaleChanged(
        view: WebView,
        oldScale: Float,
        newScale: Float,
    ) {
        originalClient?.onScaleChanged(view, oldScale, newScale)
    }

    @Deprecated(
        "Deprecated in API level 23",
        ReplaceWith("onReceivedError(WebView, WebResourceRequest, WebResourceError)"),
    )
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String,
        failingUrl: String,
    ) {
        if (config.captureErrors) {
            logger.log(
                LogLevel.ERROR,
                fieldsOf(
                    "_url" to failingUrl,
                    "_error_code" to errorCode.toString(),
                    "_description" to description,
                    "_source" to "webview",
                    "_mode" to "native",
                ),
            ) {
                "webview.error"
            }
        }
        @Suppress("DEPRECATION")
        originalClient?.onReceivedError(view, errorCode, description, failingUrl)
    }

    @Deprecated(
        "Deprecated in API level 24",
        ReplaceWith("shouldOverrideUrlLoading(WebView, WebResourceRequest)"),
    )
    override fun shouldOverrideUrlLoading(
        view: WebView,
        url: String,
    ): Boolean {
        @Suppress("DEPRECATION")
        return originalClient?.shouldOverrideUrlLoading(view, url) ?: false
    }

    override fun onPageCommitVisible(
        view: WebView,
        url: String,
    ) {
        originalClient?.onPageCommitVisible(view, url)
    }

    override fun onUnhandledKeyEvent(
        view: WebView,
        event: KeyEvent,
    ) {
        originalClient?.onUnhandledKeyEvent(view, event)
    }
}
