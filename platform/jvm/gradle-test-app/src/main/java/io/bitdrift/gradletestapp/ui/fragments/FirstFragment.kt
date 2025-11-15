// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppExitReason
import io.bitdrift.gradletestapp.data.model.DiagnosticsAction
import io.bitdrift.gradletestapp.data.model.NavigationAction
import io.bitdrift.gradletestapp.data.repository.AppExitRepository
import io.bitdrift.gradletestapp.data.repository.NetworkTestingRepository
import io.bitdrift.gradletestapp.data.repository.SdkRepository
import io.bitdrift.gradletestapp.diagnostics.fatalissues.FatalIssueGenerator
import io.bitdrift.gradletestapp.ui.compose.MainScreen
import io.bitdrift.gradletestapp.ui.viewmodel.MainViewModel
import io.bitdrift.gradletestapp.ui.viewmodel.MainViewModelFactory
import kotlin.system.exitProcess

/**
 * Modern Fragment using Jetpack Compose following MVVM architecture
 * Replaces the old XML-based implementation with a clean Compose UI
 */
class FirstFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels {
        val appContext = requireContext().applicationContext as Application
        MainViewModelFactory(
            appContext,
            SdkRepository(appContext),
            NetworkTestingRepository(),
            AppExitRepository(),
        )
    }

    private var _composeView: ComposeView? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val composeView get() = _composeView!!
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var sharedPreferences: SharedPreferences

    private var firstFragmentToCopySessionSpan: Span? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        firstFragmentToCopySessionSpan =
            Logger.startSpan("CreateFragmentToCopySessionClick", LogLevel.INFO)

        _composeView = ComposeView(requireContext())

        clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    val uiState = viewModel.uiState.collectAsState().value
                    MainScreen(
                        uiState = uiState,
                        onAction = { action ->
                            when (action) {
                                is NavigationAction.NavigateToConfig -> {
                                    Logger.logScreenView("config_fragment")
                                    findNavController().navigate(R.id.action_FirstFragment_to_ConfigFragment)
                                }

                                is NavigationAction.NavigateToWebView -> {
                                    Logger.logScreenView("web_view_fragment")
                                    findNavController().navigate(R.id.action_FirstFragment_to_WebViewFragment)
                                }

                                is NavigationAction.NavigateToCompose -> {
                                    Logger.logScreenView("compose_second_fragment")
                                    findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
                                }

                                is NavigationAction.NavigateToXml -> {
                                    Logger.logScreenView("xml_demo_fragment")
                                    findNavController().navigate(R.id.action_FirstFragment_to_XmlFragment)
                                }

                                is DiagnosticsAction.ForceAppExit -> {
                                    forceAppExit(uiState.diagnostics.selectedAppExitReason)
                                }

                                else -> viewModel.handleAction(action)
                            }
                        },
                    )
                }
            }
        }
        return composeView
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(view.context)

        Logger.logScreenView("first_fragment")

        firstFragmentToCopySessionSpan?.end(SpanResult.SUCCESS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _composeView = null
    }

    @SuppressLint("VisibleForTests")
    private fun forceAppExit(reason: AppExitReason) {
        when (reason) {
            AppExitReason.NON_FATAL ->
                _root_ide_package_.io.bitdrift.capture.reports.jvmcrash.CaptureUncaughtExceptionHandler
                    .createNonFatal()
            AppExitReason.ANR_BLOCKING_GET -> FatalIssueGenerator.forceBlockingGetAnr()
            AppExitReason.ANR_BROADCAST_RECEIVER ->
                FatalIssueGenerator.forceBroadcastReceiverAnr(
                    requireContext(),
                )

            AppExitReason.ANR_COROUTINES -> FatalIssueGenerator.forceCoroutinesAnr()
            AppExitReason.ANR_DEADLOCK -> FatalIssueGenerator.forceDeadlockAnr()
            AppExitReason.ANR_GENERIC -> FatalIssueGenerator.forceGenericAnr(requireContext())
            AppExitReason.ANR_SLEEP_MAIN_THREAD -> FatalIssueGenerator.forceThreadSleepAnr()
            AppExitReason.APP_CRASH_COROUTINE_EXCEPTION -> FatalIssueGenerator.forceCoroutinesCrash()
            AppExitReason.APP_CRASH_REGULAR_JVM_EXCEPTION -> FatalIssueGenerator.forceUnhandledException()
            AppExitReason.APP_CRASH_RX_JAVA_EXCEPTION -> FatalIssueGenerator.forceRxJavaException()
            AppExitReason.APP_CRASH_OUT_OF_MEMORY -> FatalIssueGenerator.forceOutOfMemoryCrash()
            AppExitReason.NATIVE_CAPTURE_DESTROY_CRASH -> FatalIssueGenerator.forceCaptureNativeCrash()
            AppExitReason.NATIVE_SIGSEGV -> FatalIssueGenerator.forceNativeSegmentationFault()
            AppExitReason.NATIVE_SIGBUS -> FatalIssueGenerator.forceNativeBusError()
            AppExitReason.SYSTEM_EXIT -> exitProcess(0)
        }
    }
}
