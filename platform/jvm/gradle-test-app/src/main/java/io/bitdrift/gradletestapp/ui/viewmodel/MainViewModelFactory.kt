// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.bitdrift.gradletestapp.data.repository.AppExitRepository
import io.bitdrift.gradletestapp.data.repository.NetworkTestingRepository
import io.bitdrift.gradletestapp.data.repository.SdkRepository

class MainViewModelFactory(
    private val application: Application,
    private val sdkRepository: SdkRepository,
    private val networkTestingRepository: NetworkTestingRepository,
    private val appExitRepository: AppExitRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(application, sdkRepository, networkTestingRepository, appExitRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
