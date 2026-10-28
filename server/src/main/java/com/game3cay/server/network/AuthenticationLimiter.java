package com.game3cay.server.network;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/** Shared per-server limits are applied before expensive password hashing. */
final class AuthenticationLimiter {
    private static final long WINDOW_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
    private record Window(long start, int attempts) {}
    private final LongSupplier clock;
    private final Map<String, Window> attempts = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Window> entry) { return size() > 10_000; }
    };

    AuthenticationLimiter() { this(System::nanoTime); }
    AuthenticationLimiter(LongSupplier clock) { this.clock = clock; }

    synchronized boolean allow(String ip, String username, boolean registration) {
        long now = clock.getAsLong();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().start() >= WINDOW_NANOS);
        if (!take("ip:" + ip, 30, now)) return false;
        if (registration && !take("register:" + ip, 10, now)) return false;
        return take("account:" + username.strip().toLowerCase(Locale.ROOT), 10, now);
    }

    private boolean take(String key, int maximum, long now) {
        Window window = attempts.get(key);
        if (window != null && window.attempts() >= maximum) return false;
        attempts.put(key, new Window(window == null ? now : window.start(), window == null ? 1 : window.attempts() + 1));
        return true;
    }
}
