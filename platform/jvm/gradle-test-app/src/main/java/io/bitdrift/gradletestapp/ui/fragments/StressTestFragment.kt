// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.gradletestapp.data.repository.AppExitRepository
import io.bitdrift.gradletestapp.data.repository.NetworkTestingRepository
import io.bitdrift.gradletestapp.data.repository.SdkRepository
import io.bitdrift.gradletestapp.data.repository.StressTestRepository
import io.bitdrift.gradletestapp.ui.compose.StressTestScreen
import io.bitdrift.gradletestapp.ui.viewmodel.MainViewModel
import io.bitdrift.gradletestapp.ui.viewmodel.MainViewModelFactory

class StressTestFragment : Fragment() {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    StressTestScreen(
                        onAction = { action -> viewModel.handleAction(action) },
                        onNavigateBack = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.logScreenView("stress_test_fragment")
    }
}

