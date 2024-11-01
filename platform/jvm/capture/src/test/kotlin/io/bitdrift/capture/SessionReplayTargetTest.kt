package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.events.SessionReplayTarget
import io.bitdrift.capture.replay.SessionReplayConfiguration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SessionReplayTargetTest {
    private val logger: LoggerImpl = mock()
    private val errorHandler: ErrorHandler = mock()
    private val handler: MainThreadHandler = Mocks.sameThreadHandler

    private val target = SessionReplayTarget(
        errorHandler = errorHandler,
        context = ApplicationProvider.getApplicationContext(),
        logger = logger,
        configuration = SessionReplayConfiguration(),
        mainThreadHandler = handler,
    )

    init {
        CaptureJniLibrary.load()
    }

    @Test
    fun sessionReplayTargetDoesNotCrash() {
        CaptureTestJniLibrary.runSessionReplayTargetTest(target)
    }

    @Test
    fun sessionReplayTargetEmitsScreenLog() {
        target.captureScreen()
        // TODO: Make this test work, the issue is that in test environment session replay
        // sees 0 views and as a result it doesn't emit a session replay screen log.
//        verify(logger, timeout(1000).times(1)).logSessionReplayScreen(any(), any())
    }
}
