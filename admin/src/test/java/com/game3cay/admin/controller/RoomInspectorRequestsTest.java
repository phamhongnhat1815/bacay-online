package com.game3cay.admin.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomInspectorRequestsTest {
    @Test void rejectsResponsesForClosedOrReplacedInspectors() {
        RoomInspectorRequests requests = new RoomInspectorRequests();
        var roomA = requests.next(1);
        assertTrue(requests.accepts(roomA, 1));
        var roomB = requests.next(2);
        assertFalse(requests.accepts(roomA, 1));
        assertFalse(requests.accepts(roomB, 1));
        assertTrue(requests.accepts(roomB, 2));
        requests.cancel();
        assertFalse(requests.accepts(roomB, 2));
    }

    @Test void rejectsAnOlderPollForTheSameRoom() {
        RoomInspectorRequests requests = new RoomInspectorRequests();
        var older = requests.next(1);
        var newer = requests.next(1);
        assertTrue(requests.accepts(newer, 1));
        assertFalse(requests.accepts(older, 1));
    }

    @Test void aSlowPollCanApplyWhileANewerRequestIsStillInFlight() {
        RoomInspectorRequests requests = new RoomInspectorRequests();
        var older = requests.next(1);
        var newer = requests.next(1);
        assertTrue(requests.accepts(older, 1));
        assertTrue(requests.accepts(newer, 1));
    }
}
