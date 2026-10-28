package com.game3cay.controller;

import com.game3cay.shared.network.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectStateTest {
    @Test void repeatedFailuresExhaustAttemptsAndReleaseBusyState() {
        ReconnectState reconnect = new ReconnectState(5);
        assertTrue(reconnect.begin());
        assertFalse(reconnect.begin());
        for (int i = 1; i <= 5; i++) {
            assertTrue(reconnect.nextAttempt());
            assertEquals(i, reconnect.attempts());
            assertTrue(reconnect.active());
        }
        assertFalse(reconnect.nextAttempt());
        assertFalse(reconnect.active());
        assertTrue(reconnect.begin());
        assertTrue(reconnect.nextAttempt());
        assertEquals(1, reconnect.attempts());
    }

    @Test void authFailuresTerminateWhileOldSessionAndThrottlingCanRetry() {
        assertTrue(ReconnectState.retryable(ErrorCode.ALREADY_LOGGED_IN));
        assertTrue(ReconnectState.retryable(ErrorCode.RATE_LIMIT_EXCEEDED));
        assertFalse(ReconnectState.retryable(ErrorCode.USER_BLOCKED));
        assertFalse(ReconnectState.retryable(ErrorCode.INVALID_CREDENTIALS));
        assertFalse(ReconnectState.retryable(null));
        ReconnectState reconnect = new ReconnectState(2);
        reconnect.begin();
        reconnect.nextAttempt();
        reconnect.finish();
        assertFalse(reconnect.active());
        assertFalse(reconnect.nextAttempt());
    }
}
