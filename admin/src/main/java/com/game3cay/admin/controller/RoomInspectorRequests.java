package com.game3cay.admin.controller;

/** Reject responses from replaced inspectors and responses older than the last applied update. */
final class RoomInspectorRequests {
    record Ticket(long roomId, long generation) {}
    private long generation;
    private long minimumGeneration;
    private long appliedGeneration;
    private Ticket latest;

    Ticket next(long roomId) {
        long nextGeneration = ++generation;
        if (latest == null || latest.roomId() != roomId) minimumGeneration = nextGeneration;
        latest = new Ticket(roomId, nextGeneration);
        return latest;
    }

    boolean accepts(Ticket request, long responseRoomId) {
        if (request == null || latest == null || request.roomId() != responseRoomId
                || latest.roomId() != responseRoomId || request.generation() < minimumGeneration
                || request.generation() <= appliedGeneration || request.generation() > latest.generation()) return false;
        appliedGeneration = request.generation();
        return true;
    }

    void cancel() { latest = null; minimumGeneration = generation + 1; }
}
