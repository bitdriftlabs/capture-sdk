// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.bitdrift.capture.LogLevel
import io.bitdrift.gradletestapp.data.model.*
import io.bitdrift.gradletestapp.data.repository.SdkRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for the main app screen following MVVM architecture
 * Manages UI state and handles user actions
 */
class MainViewModel(
    private val sdkRepository: SdkRepository,
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
                    ),
            )
        }
        updateSdkState()
        checkAutoInitialization()
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

            is SessionAction.StartNewSession -> startNewSession()
            is SessionAction.GenerateDeviceCode -> generateDeviceCode()
            is SessionAction.CopySessionUrl -> copySessionUrl()

            is DiagnosticsAction.LogMessage -> logMessage()
            is DiagnosticsAction.ForceAppExit -> forceAppExit()
            is DiagnosticsAction.UpdateAppExitReason -> updateAppExitReason(action.reason)

            is NetworkTestAction.PerformOkHttpRequest -> performOkHttpRequest()
            is NetworkTestAction.PerformGraphQlRequest -> performGraphQlRequest()

            ClearError -> clearError()
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
                val errorMessage = result.exceptionOrNull()?.message ?: "Failed to generate device code"
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

    private fun performOkHttpRequest() {
        Timber.i("Performing OkHttp request")
    }

    private fun performGraphQlRequest() {
        Timber.i("Performing GraphQL request")
    }

    private fun logMessage() {
        viewModelScope.launch {
            val level = _uiState.value.config.selectedLogLevel
            val message = "Log message with level: $level"
            sdkRepository.logMessage(level, message)
        }
    }

    private fun forceAppExit() {
        Timber.i("Forcing app exit with reason: ${_uiState.value.diagnostics.selectedAppExitReason}")
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
