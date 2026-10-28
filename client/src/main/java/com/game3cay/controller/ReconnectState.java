package com.game3cay.controller;

import com.game3cay.shared.network.ErrorCode;

/** Bounded reconnect lifecycle, confined to the Swing event thread. */
final class ReconnectState {
    private final int maxAttempts;
    private boolean active;
    private int attempts;

    ReconnectState(int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts");
        this.maxAttempts = maxAttempts;
    }

    boolean begin() {
        if (active) return false;
        active = true;
        attempts = 0;
        return true;
    }

    boolean nextAttempt() {
        if (!active || attempts >= maxAttempts) {
            finish();
            return false;
        }
        attempts++;
        return true;
    }

    boolean active() { return active; }
    int attempts() { return attempts; }

    void finish() {
        active = false;
        attempts = 0;
    }

    static boolean retryable(ErrorCode error) {
        return error == ErrorCode.ALREADY_LOGGED_IN
                || error == ErrorCode.RATE_LIMIT_EXCEEDED
                || error == ErrorCode.INTERNAL_ERROR;
    }
}
