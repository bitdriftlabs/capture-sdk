package io.bitdrift.flappyclippy.view

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.bitdrift.flappyclippy.model.*
import io.bitdrift.flappyclippy.ui.theme.ForegroundEarthYellow
import io.bitdrift.flappyclippy.util.LogUtil
import io.bitdrift.flappyclippy.viewmodel.GameViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameScreen(
    clickable: Clickable = Clickable()
) {
    LogUtil.printLog(message = "GameScreen()")
    val viewModel: GameViewModel = viewModel()
    val viewState by viewModel.viewState.collectAsState()
    var screenSize: Pair<Int, Int>

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForegroundEarthYellow)
            // .background(DefaultBlackBackground)
            .run {
                pointerInteropFilter {
                    // Send lift action to let bird up.
                    // Todo only work when running status.
                    when (it.action) {
                        MotionEvent.ACTION_DOWN -> {
                            LogUtil.printLog(message = "GameScreen ACTION_DOWN status:${viewState.gameStatus}")
                            if (viewState.gameStatus == GameStatus.Waiting) clickable.onStart()
                            else if (viewState.isRunning) clickable.onTap()
                            else return@pointerInteropFilter false
                        }

                        // Todo send quick lift when long tap event.
                        MotionEvent.ACTION_MOVE -> {
                            LogUtil.printLog(message = "GameScreen ACTION_MOVE")
                            return@pointerInteropFilter false
                        }

                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                            LogUtil.printLog(message = "GameScreen ACTION_CANCEL/UP")
                            return@pointerInteropFilter false
                        }
                    }

                    false
                }
            }
    ) {

        Box(modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .onGloballyPositioned {
                screenSize = Pair(it.size.width, it.size.height)
                LogUtil.printLog(
                    message = "Screen size:[${screenSize.first},${screenSize.second}]\n" +
                            "Zone size:[${viewState.playZoneSize.first},${viewState.playZoneSize.second}]"
                )

                if (viewState.playZoneSize.first <= 0 || viewState.playZoneSize.second <= 0) {
                    LogUtil.printLog(message = "Screen size first detect.")
                    viewModel.dispatch(GameAction.ScreenSizeDetect, screenSize)
                }
            }
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
        ) {
            LogUtil.printLog(message = "GameScreen Box Lambda")

            // Far background
            FarBackground()

            // Put two group pipes
            PipeCouple(
                modifier = Modifier.fillMaxSize(),
                state = viewState,
                pipeIndex = 0
            )

            PipeCouple(
                modifier = Modifier.fillMaxSize(),
                state = viewState,
                pipeIndex = 1
            )

            val playZoneWidthInDP = with(LocalDensity.current) {
                viewState.playZoneSize.first.toDp()
            }

            val playZoneHeightInDP = with(LocalDensity.current) {
                viewState.playZoneSize.second.toDp()
            }

            LogUtil.printLog(message = "Zone:[$playZoneWidthInDP,$playZoneHeightInDP]\n" +
                    "first pipe:${viewState.pipeStateList[0]}\n" +
                    "second pipe:${viewState.pipeStateList[1]}\n" +
                    "bird height offset:${viewState.birdState.birdHeight}"
            )

            // Check hit first or second pipe.
            // And send hit pipe action if bird hit any pipe.
            if (viewState.isRunning) {
                viewState.pipeStateList.forEachIndexed { pipeIndex, pipeState ->
                    CheckPipeStatus(
                        viewState.birdState.birdHeight,
                        pipeState,
                        playZoneWidthInDP,
                        playZoneHeightInDP
                    ).also {
                        viewModel.dispatchPipeStatus(it, pipeIndex)
                        when (it) {
                            PipeStatus.BirdHit -> {
                                LogUtil.printLog(message = "Send hit pipe action")
                                viewModel.dispatch(GameAction.HitPipe)
                            }

                            PipeStatus.BirdCrossed -> {
                                LogUtil.printLog(message = "Send crossed pipe action with index:$pipeIndex")
                                viewModel.dispatch(GameAction.CrossedPipe, pipeIndex = pipeIndex)
                            }

                            // No care other status.
                            else -> {}
                        }
                    }
                }
            }

            if (viewState.gameStatus == GameStatus.Running) {
                RealTimeBoard(Modifier.fillMaxSize(), viewState.score)
            }

            // Put bird
            Bird(
                modifier = Modifier.fillMaxSize(),
                state = viewState
            )

            if (viewState.gameStatus == GameStatus.Over) {
                GameOverBoard(Modifier.fillMaxSize(), viewState.score, viewState.bestScore, clickable)
            }
        }

        Box(modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .fillMaxWidth()
            .fillMaxHeight(0.15f)
        ) {
            NearForeground(
                modifier = Modifier.fillMaxSize(),
                state = viewState
            )
        }
    }
}

@Composable
fun CheckPipeStatus(birdHeightOffset: Dp, pipeState: PipeState, zoneWidth: Dp, zoneHeight: Dp): PipeStatus {
    LogUtil.printLog(message = "CheckPipeStatus()")
    if (zoneHeight == 0.dp || pipeState.counted) {
        return PipeStatus.BirdComing
    }

    val birdTop = (zoneHeight - BirdSizeHeight) / 2 + birdHeightOffset
    val birdBottom = (zoneHeight + BirdSizeHeight) / 2 + birdHeightOffset
    val birdCrossedEnd = pipeState.offset - PipeCoverWidth < - zoneWidth / 2 - BirdSizeWidth / 2
    val birdCrossedStart = pipeState.offset - PipeCoverWidth < - zoneWidth / 2 + BirdSizeWidth / 2
    if (birdCrossedEnd) {
        LogUtil.printLog(message = "Bird crossed successfully")
        return PipeStatus.BirdCrossed
    } else if (birdCrossedStart) {
        if (birdTop < pipeState.upHeight || birdBottom > zoneHeight - pipeState.downHeight) {
            LogUtil.printLog(message = "Bird hit unluckily")
            return PipeStatus.BirdHit
        }

        LogUtil.printLog(message = "Bird still crossing, good luck.")
        if (birdBottom + BirdFallVelocity > zoneHeight - pipeState.downHeight) {
            return PipeStatus.BirdComingLow
        }
        return PipeStatus.BirdCrossing
    } else {
        LogUtil.printLog(message = "Bird is coming")
        if (birdBottom + BirdFallVelocity > zoneHeight - pipeState.downHeight) {
            return PipeStatus.BirdComingLow
        } else if (birdTop < pipeState.upHeight) {
            return PipeStatus.BirdComingHigh
        } else {
            return PipeStatus.BirdComing
        }
    }
 }

@Preview(widthDp = 411, heightDp = 840)
@Composable
fun PreviewGameScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForegroundEarthYellow)
    ) {

        Box(modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
        ) {
            LogUtil.printLog(message = "GameScreen Box Lambda")

            // Far background
            FarBackground()

            PipeScreen()

            PreviewBird()

            RealTimeBoardPreview()
        }

        previewForeground()
    }
}

data class Clickable(
    val onStart: () -> Unit = {},
    val onTap: () -> Unit = {},
    val onRestart: () -> Unit = {},
    val onExit: () -> Unit = {},
    val onDemo: () -> Unit = {},
)
