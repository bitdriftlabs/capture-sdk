// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.NavigationAction
import io.bitdrift.gradletestapp.data.repository.AppExitRepository
import io.bitdrift.gradletestapp.data.repository.NetworkTestingRepository
import io.bitdrift.gradletestapp.data.repository.SdkRepository
import io.bitdrift.gradletestapp.data.repository.StressTestRepository
import io.bitdrift.gradletestapp.ui.compose.MainScreen
import io.bitdrift.gradletestapp.ui.service.SendTelemetryService
import io.bitdrift.gradletestapp.ui.viewmodel.MainViewModel
import io.bitdrift.gradletestapp.ui.viewmodel.MainViewModelFactory

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
            StressTestRepository(),
        )
    }

    private var _composeView: ComposeView? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val composeView get() = _composeView!!
    private lateinit var clipboardManager: ClipboardManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
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
                                    val bundle = Bundle().apply {
                                        putString(WebViewFragment.ARG_URL, action.url)
                                    }
                                    findNavController().navigate(R.id.action_FirstFragment_to_WebViewFragment, bundle)
                                }

                                is NavigationAction.NavigateToCompose -> {
                                    Logger.logScreenView("compose_second_fragment")
                                    findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
                                }

                                is NavigationAction.NavigateToXml -> {
                                    Logger.logScreenView("xml_demo_fragment")
                                    findNavController().navigate(R.id.action_FirstFragment_to_XmlFragment)
                                }

                                is NavigationAction.NavigateToDialogAndModals -> {
                                    Logger.logScreenView("dialog_and_modals_fragment")
                                    findNavController().navigate(R.id.action_FirstFragment_to_DialogAndModalsFragment)
                                }

                                is NavigationAction.NavigateToStressTest -> {
                                    Logger.logScreenView("stress_test_fragment")
                                    findNavController().navigate(R.id.action_FirstFragment_to_StressTestFragment)
                                }

                                is NavigationAction.InvokeService -> {
                                    ContextCompat.startForegroundService(
                                        requireContext(),
                                        SendTelemetryService.createReportIntent(requireContext()),
                                    )
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

        Logger.logScreenView("first_fragment")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _composeView = null
    }
}
