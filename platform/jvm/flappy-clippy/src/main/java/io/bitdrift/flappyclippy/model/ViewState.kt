package io.bitdrift.flappyclippy.model

// Managed by view model, used to refresh ui.
data class ViewState(
    val gameStatus: GameStatus = GameStatus.Waiting,

    val gameMode: GameMode = GameMode.Normal,

    val birdState: BirdState = BirdState(),

    var pipeStatus: PipeStatus = PipeStatus.BirdComing,

    val pipeStateList: List<PipeState> = PipeStateList,
    // Index that identify which pipe couple to reset or count score.
    var targetPipeIndex: Int = -1,

    val roadStateList: List<RoadState> = RoadStateList,
    // Index that identify which road to reset.
    var targetRoadIndex: Int = -1,

    var playZoneSize: Pair<Int, Int> = Pair(0, 0),

    val score: Int = 0,
    val bestScore: Int = 0,
) {
    val isRunning get() = gameStatus == GameStatus.Running
    val isLifting get() = isRunning && birdState.isLifting
    val isFalling get() = isRunning && !birdState.isLifting
    val isQuickFalling get() = gameStatus == GameStatus.Dying
    val isOver get() = gameStatus == GameStatus.Over

    fun reset(bestScore: Int, gameMode: GameMode = GameMode.Normal, gameStatus: GameStatus = GameStatus.Waiting): ViewState =
        ViewState(bestScore = bestScore, gameStatus = gameStatus, gameMode = gameMode)
}

enum class GameMode {
    Normal,
    Demo
}

// Enum that manage game status.
enum class GameStatus {
    Waiting, // wait to start
    Running,
    Dying, // hit pipe and dying
    Over
}

// User or view action to trigger game.
sealed class GameAction {
    object Start : GameAction()
    object AutoTick : GameAction()
    object TouchLift : GameAction()

    object ScreenSizeDetect : GameAction()
    object PipeExit : GameAction()
    object RoadExit : GameAction()

    object HitPipe : GameAction()
    object HitGround : GameAction()
    object CrossedPipe : GameAction()

    object Restart : GameAction()
    object Demo : GameAction()
}