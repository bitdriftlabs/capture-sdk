package io.bitdrift.capture.providers.session

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.IPreferences
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider.Companion.SESSION_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SessionPersistenceImplTest {
    private val preferences: IPreferences = mock()
    private val sessionPersistenceImpl = SessionPersistenceImpl(preferences)

    @Test
    fun saveCurrentSessionId_withValidUuid_shouldPersistInBlockingManner() {
        sessionPersistenceImpl.saveCurrentSessionId(SESSION_ID)

        verify(preferences).setString("session_uuid", SESSION_ID, true)
    }

    @Test
    fun getPreviousSessionId_withNullSession_shouldMatchExpectedKey() {
        whenever(preferences.getString(any())).thenReturn(SESSION_ID)

        val sessionId = sessionPersistenceImpl.getPreviousSessionId()

        assertThat(sessionId).isEqualTo(SESSION_ID)
        verify(preferences).getString("session_uuid")
    }
}
