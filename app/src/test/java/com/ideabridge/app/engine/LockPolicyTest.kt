package com.ideabridge.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    @Test fun neverRelocksWhenTheLockIsDisabled() {
        assertFalse(LockPolicy.shouldRelock(enabled = false, backgroundedAt = 1_000, now = 10_000_000))
    }

    @Test fun shortAbsenceDoesNotRelock() {
        assertFalse(LockPolicy.shouldRelock(true, backgroundedAt = 1_000, now = 1_000 + LockPolicy.TIMEOUT_MS - 1))
    }

    @Test fun absenceOfThirtySecondsOrMoreRelocks() {
        assertTrue(LockPolicy.shouldRelock(true, backgroundedAt = 1_000, now = 1_000 + LockPolicy.TIMEOUT_MS))
        assertTrue(LockPolicy.shouldRelock(true, backgroundedAt = 1_000, now = 1_000 + 10 * LockPolicy.TIMEOUT_MS))
    }

    @Test fun anAppThatNeverWentToTheBackgroundIsNotRelockedHere() {
        assertFalse(LockPolicy.shouldRelock(true, backgroundedAt = 0, now = 99_999_999))
    }

    @Test fun theTimeoutIsConfigurable() {
        assertTrue(LockPolicy.shouldRelock(true, 1_000, 2_000, timeoutMs = 1_000))
        assertFalse(LockPolicy.shouldRelock(true, 1_000, 1_999, timeoutMs = 1_000))
    }
}
