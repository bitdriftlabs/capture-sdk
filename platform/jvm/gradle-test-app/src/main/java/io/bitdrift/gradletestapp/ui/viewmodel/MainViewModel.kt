// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.AppExitReason
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.data.model.ClearError
import io.bitdrift.gradletestapp.data.model.ConfigAction
import io.bitdrift.gradletestapp.data.model.DiagnosticsAction
import io.bitdrift.gradletestapp.data.model.FeatureFlagsTestAction
import io.bitdrift.gradletestapp.data.model.NavigationAction
import io.bitdrift.gradletestapp.data.model.NetworkTestAction
import io.bitdrift.gradletestapp.data.model.SessionAction
import io.bitdrift.gradletestapp.data.model.StressTestAction
import io.bitdrift.gradletestapp.data.repository.AppExitRepository
import io.bitdrift.gradletestapp.data.repository.NetworkTestingRepository
import io.bitdrift.gradletestapp.data.repository.SdkRepository
import io.bitdrift.gradletestapp.data.repository.StressTestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for the main app screen following MVVM architecture
 * Manages UI state and handles user actions
 */
class MainViewModel(
    private val application: Application,
    private val sdkRepository: SdkRepository,
    private val networkTestingRepository: NetworkTestingRepository,
    private val appExitRepository: AppExitRepository,
    private val stressTestRepository: StressTestRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            initializeSdkConfig()
        }
    }

    private suspend fun initializeSdkConfig() {
        val cfg = sdkRepository.getConfig()
        _uiState.update {
            it.copy(
                config =
                    it.config.copy(
                        apiKey = cfg.apiKey,
                        apiUrl = cfg.apiUrl,
                        sessionStrategy = cfg.sessionStrategy,
                        isDeferredStart = cfg.isDeferredStart,
                        isSleepModeEnabled = cfg.isSleepModeEnabled,
                    ),
            )
        }
        updateSdkState()
        checkAutoInitialization()

        if (sdkRepository.isSdkInitialized()) {
            runHealthCheck()
        }
    }

    private fun checkAutoInitialization() {
        val state = _uiState.value
        val config = state.config
        val session = state.session

        if (!config.isDeferredStart && !session.isSdkInitialized) {
            if (config.apiKey.isBlank() || config.apiUrl.isBlank()) {
                _uiState.update {
                    it.copy(
                        error = "SDK auto-initialization failed: API key or URL not configured. Please set them in Settings.",
                    )
                }
            }
        }
    }

    fun handleAction(action: AppAction) {
        when (action) {
            is ConfigAction.InitializeSdk -> initializeSdk()
            is ConfigAction.UpdateApiKey -> updateApiKey(action.apiKey)
            is ConfigAction.UpdateApiUrl -> updateApiUrl(action.apiUrl)
            is ConfigAction.UpdateLogLevel -> updateLogLevel(action.logLevel)
            is ConfigAction.SetSleepModeEnabled -> setSleepModeEnabled(action.enabled)

            is SessionAction.StartNewSession -> startNewSession()
            is SessionAction.GenerateDeviceCode -> generateDeviceCode()
            is SessionAction.CopySessionUrl -> copySessionUrl()

            is DiagnosticsAction.LogMessage -> logMessage()
            is DiagnosticsAction.ForceAppExit -> forceAppExit()
            is DiagnosticsAction.UpdateAppExitReason -> updateAppExitReason(action.reason)

            is NetworkTestAction.PerformOkHttpRequest -> {
                networkTestingRepository.performOkHttpRequest()
            }
            is NetworkTestAction.PerformGraphQlRequest -> {
                networkTestingRepository.performGraphQlRequest()
            }
            is NetworkTestAction.PerformRetrofitRequest -> {
                networkTestingRepository.performRetrofitRequest()
            }

            is FeatureFlagsTestAction.AddOneFeatureFlag -> addOneFeatureFlag()
            is FeatureFlagsTestAction.AddManyFeatureFlags -> addManyFeatureFlags()

            is StressTestAction.IncreaseMemoryPressure -> stressTestRepository.increaseMemoryPressure(action.targetPercent)
            is StressTestAction.TriggerMemoryPressureAnr -> stressTestRepository.triggerMemoryPressureAnr()
            is StressTestAction.TriggerJankyFrames -> stressTestRepository.triggerJankyFrames(action.type.durationMs)
            is StressTestAction.TriggerStrictModeViolation -> stressTestRepository.triggerStrictModeViolation()

            is ClearError -> clearError()

            // For now, navigation actions are handled at the Fragment level
            is NavigationAction.NavigateToCompose -> {}
            is NavigationAction.NavigateToConfig -> {}
            is NavigationAction.NavigateToWebView -> {}
            is NavigationAction.NavigateToXml -> {}
            is NavigationAction.NavigateToDialogAndModals -> {}
            is NavigationAction.NavigateToStressTest -> {}
        }
    }

    private fun initializeSdk() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val config = _uiState.value.config

            sdkRepository.saveApiKey(config.apiKey)
            sdkRepository.saveApiUrl(config.apiUrl)

            val success = sdkRepository.initializeSdk(config.apiKey, config.apiUrl)
            if (success) {
                updateSdkState()
                runHealthCheck()
                _uiState.update { it.copy(isLoading = false, error = null) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to initialize SDK. Please check your API key and URL.",
                    )
                }
            }
        }
    }

    private fun startNewSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val sessionId = sdkRepository.startNewSession()
            if (sessionId != null) {
                updateSdkState()
                runHealthCheck()
                _uiState.update { it.copy(isLoading = false) }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to start new session")
                }
            }
        }
    }

    private fun runHealthCheck() {
        viewModelScope.launch {
            // GenerateDeviceCode is a good proxy to determine connectivity
            val result = sdkRepository.generateDeviceCode()
            val isValid = result.isSuccess
            val errorMessage = result.exceptionOrNull()?.message
            _uiState.update { state ->
                state.copy(
                    session =
                        state.session.copy(
                            isDeviceCodeValid = isValid,
                            deviceCodeError = if (!isValid) errorMessage else null,
                            deviceCode = if (isValid) result.getOrNull() else state.session.deviceCode,
                        ),
                )
            }
        }
    }

    private fun generateDeviceCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = sdkRepository.generateDeviceCode()
            if (result.isSuccess) {
                val deviceCode = result.getOrNull()
                _uiState.update {
                    it.copy(
                        session =
                            it.session.copy(
                                deviceCode = deviceCode,
                                isDeviceCodeValid = true,
                                deviceCodeError = null,
                            ),
                        isLoading = false,
                    )
                }
            } else {
                val errorMessage =
                    result.exceptionOrNull()?.message ?: "Failed to generate device code"
                _uiState.update {
                    it.copy(
                        session =
                            it.session.copy(
                                isDeviceCodeValid = false,
                                deviceCodeError = errorMessage,
                            ),
                        isLoading = false,
                        error = errorMessage,
                    )
                }
            }
        }
    }

    private fun copySessionUrl() {
        val sessionUrl = sdkRepository.getSessionUrl()
        if (sessionUrl != null) {
            Timber.i("Session URL copied: $sessionUrl")
        } else {
            _uiState.update { it.copy(error = "No session URL available") }
        }
    }

    @OptIn(ExperimentalBitdriftApi::class)
    private fun addOneFeatureFlag() {
        Timber.i("Adding one feature flag exposure")
        Logger.setFeatureFlagExposure("myflag", "myvariant")
    }

    @OptIn(ExperimentalBitdriftApi::class)
    private fun addManyFeatureFlags() {
        Timber.i("Adding many feature flag exposures")
        // Only single flag exposures are supported now
        for (i in 1..10000) {
            Logger.setFeatureFlagExposure("flag_$i")
        }
    }

    private fun logMessage() {
        viewModelScope.launch {
            val level = _uiState.value.config.selectedLogLevel
            val message = "Log message with level: $level"
            sdkRepository.logMessage(level, message)
        }
    }

    private fun setSleepModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sdkRepository.setSleepModeEnabled(enabled)
            _uiState.update { state ->
                state.copy(config = state.config.copy(isSleepModeEnabled = enabled))
            }
            Timber.i("Sleep mode ${if (enabled) "enabled" else "disabled"}")
        }
    }

    private fun forceAppExit() {
        val reason = _uiState.value.diagnostics.selectedAppExitReason
        Timber.i("Forcing app exit with reason: $reason")
        Thread({
            appExitRepository.triggerAppExit(application, reason)
        }, "fatal-issue-trigger").start()
    }

    private fun updateApiKey(apiKey: String) {
        _uiState.update {
            it.copy(config = it.config.copy(apiKey = apiKey))
        }
    }

    private fun updateApiUrl(apiUrl: String) {
        _uiState.update {
            it.copy(config = it.config.copy(apiUrl = apiUrl))
        }
    }

    private fun updateLogLevel(logLevel: LogLevel) {
        _uiState.update {
            it.copy(config = it.config.copy(selectedLogLevel = logLevel))
        }
    }

    private fun updateAppExitReason(reason: AppExitReason) {
        _uiState.update {
            it.copy(diagnostics = it.diagnostics.copy(selectedAppExitReason = reason))
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun updateSdkState() {
        val sessionState =
            _uiState.value.session.copy(
                isSdkInitialized = sdkRepository.isSdkInitialized(),
                sessionId = sdkRepository.getSessionId(),
                sessionUrl = sdkRepository.getSessionUrl(),
                deviceCode = _uiState.value.session.deviceCode,
            )

        val configState =
            _uiState.value.config.copy(
                apiUrl = sdkRepository.getApiUrl(),
                sessionStrategy = sdkRepository.getSessionStrategy(),
            )

        _uiState.update {
            it.copy(
                session = sessionState,
                config = configState,
            )
        }
    }
}
