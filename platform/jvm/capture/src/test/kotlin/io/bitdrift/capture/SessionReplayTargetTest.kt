package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.common.ErrorHandler
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

    private val target = SessionReplayTarget(
        errorHandler = errorHandler,
        context = ApplicationProvider.getApplicationContext(),
        logger = logger,
        configuration = SessionReplayConfiguration(),
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
        verify(logger, timeout(1000).times(1)).logSessionReplayScreen(any(), any())
    }
}
