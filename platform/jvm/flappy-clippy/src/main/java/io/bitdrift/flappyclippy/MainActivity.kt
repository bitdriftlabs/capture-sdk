package io.bitdrift.flappyclippy

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.bitdrift.flappyclippy.model.GameAction
import io.bitdrift.flappyclippy.model.GameStatus
import io.bitdrift.flappyclippy.ui.theme.FlappyClippyTheme
import io.bitdrift.flappyclippy.util.StatusBarUtil
import io.bitdrift.flappyclippy.view.Clickable
import io.bitdrift.flappyclippy.view.GameScreen
import io.bitdrift.flappyclippy.viewmodel.GameViewModel
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.replay.ReplayLogger
import io.bitdrift.capture.replay.ReplayModule
import io.bitdrift.capture.replay.ReplayPreviewClient
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.replay.internal.EncodedScreenMetrics
import io.bitdrift.capture.replay.internal.FilteredCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.timer

private const val bitdriftAPIKey = Config.API_KEY
private val BITDRIFT_STAGING_URL = HttpUrl.Builder().scheme("https").host(Config.API_URL).build()

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    private val replayPreviewClient: ReplayPreviewClient by lazy {
        ReplayPreviewClient(ReplayModule(
            object: ErrorHandler {
                override fun handleError(detail: String, e: Throwable?) {}
            },
            object: ReplayLogger {
                override fun onScreenCaptured(encodedScreen: ByteArray, screen: FilteredCapture,
                                              metrics: EncodedScreenMetrics
                ) {}
                override fun logVerboseInternal(message: String, fields: Map<String, String>?) {}
                override fun logDebugInternal(message: String, fields: Map<String, String>?) {}
                override fun logErrorInternal(message: String, e: Throwable?, fields: Map<String, String>?) {}
            },
            SessionReplayConfiguration(),
            object: Runtime {
                override fun isEnabled(feature: RuntimeFeature): Boolean {
                    return true
                }
            }
        ), this.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = dateFormat.format(Date())
        Logger.configure(
            apiKey = bitdriftAPIKey,
            apiUrl = BITDRIFT_STAGING_URL,
            sessionStrategy = SessionStrategy.Fixed { "flappy-clippy-session-$date" }
        )

        Logger.logDebug { "Launching game app" }
        Log.d("FlappyClippy", "??? SessionID: " + Logger.sessionId)

        // Expand screen to status bar.
        StatusBarUtil.transparentStatusBar(this)

        setContent {
            FlappyClippyTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    val gameViewModel: GameViewModel = viewModel()

                    // Send a auto tick action to view model and trigger game start.
                    LaunchedEffect(key1 = Unit) {
                        while (isActive) {
                            delay(AutoTickDuration)
                            if (gameViewModel.viewState.value.gameStatus != GameStatus.Waiting) {
                                gameViewModel.dispatch(GameAction.AutoTick)
                            }
                        }
                    }

                    Flappy(Clickable(

                        onStart = {
                            gameViewModel.dispatch(GameAction.Start)
                        },

                        onTap = {
                            gameViewModel.dispatch(GameAction.TouchLift)
                        },

                        onRestart = {
                            gameViewModel.dispatch(GameAction.Restart)
                        },

                        onExit = {
                            finish()
                        }
                    ))
                }
            }
        }

        replayPreviewClient.connect()
        timer(initialDelay = 0, period = 300L ) {
            CoroutineScope(Dispatchers.Main).launch {
                replayPreviewClient.captureScreen()
            }
        }
    }
}

@Composable
fun Flappy(clickable: Clickable = Clickable()) {
    GameScreen(clickable = clickable)
}

const val AutoTickDuration = 50L // 300L Control bird and pipe speed.
