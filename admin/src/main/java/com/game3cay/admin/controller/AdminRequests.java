package com.game3cay.admin.controller;

import com.game3cay.shared.network.PacketType;
import java.util.EnumMap;

/** Tracks the login session and the most recent read for each admin view. */
final class AdminRequests {
    record Ticket(long session, PacketType type, long sequence) {}
    private final EnumMap<PacketType, Long> latestReads = new EnumMap<>(PacketType.class);
    private long session;
    private boolean active;

    long beginSession() {
        active = true;
        latestReads.clear();
        return ++session;
    }

    void endSession() {
        active = false;
        latestReads.clear();
    }

    boolean isCurrent(long expectedSession) { return active && session == expectedSession; }

    Ticket next(PacketType type, boolean latestOnly) {
        long sequence = latestOnly ? latestReads.merge(type, 1L, Long::sum) : 0;
        return new Ticket(session, type, sequence);
    }

    boolean accepts(Ticket ticket) {
        return isCurrent(ticket.session()) && (ticket.sequence() == 0
                || ticket.sequence() == latestReads.getOrDefault(ticket.type(), 0L));
    }
}
