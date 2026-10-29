package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointUiRulesTest {
    @Test void acceptanceRequiresAddressedPlayerSameWaitingRoomAndUnexpiredRequest() {
        PointRequestItem request = request(PointRequestStatus.PENDING, 2000);
        RoomSnapshot waiting = room("WAITING", 1);
        assertTrue(PointUiRules.canAccept(request, 2, waiting, 1000, false));
        assertFalse(PointUiRules.canAccept(request, 1, waiting, 1000, false));
        assertFalse(PointUiRules.canAccept(request, 2, waiting, 2000, false));
        assertFalse(PointUiRules.canAccept(request, 2, waiting, 1000, true));
        assertFalse(PointUiRules.canAccept(request, 2, room("PLAYING", 1), 1000, false));
        assertFalse(PointUiRules.canAccept(request, 2, room("WAITING", 3), 1000, false));
        assertFalse(PointUiRules.canAccept(request(PointRequestStatus.FULFILLED, 2000), 2, waiting, 1000, false));
        assertEquals("Hết hạn", PointUiRules.status(request, 2000));
    }

    @Test void amountsAndConfirmationExposeOnlyGiversBeforeAndAfterBalance() {
        assertEquals(new BigDecimal("100.25"), PointUiRules.amount(" 100.25 "));
        for (String invalid : List.of("0", "-1", "1.001", "1E+3", "not a number")) assertThrows(IllegalArgumentException.class, () -> PointUiRules.amount(invalid));
        String confirmation = PointUiRules.confirmation("Same name", 42, BigDecimal.TEN, new BigDecimal("100"));
        assertTrue(confirmation.contains("#42"));
        assertTrue(confirmation.contains("100 → 90"));
    }

    @Test void listResponseCapturedBeforeACommitCannotReopenACompletedRequest() {
        PointRequestItem pending = request(PointRequestStatus.PENDING, 2000);
        PointRequestItem completed = request(PointRequestStatus.FULFILLED, 2000);
        assertSame(completed, PointUiRules.newest(completed, pending));
        assertSame(completed, PointUiRules.newest(pending, completed));
        assertSame(pending, PointUiRules.newest(null, pending));
    }

    private static PointRequestItem request(PointRequestStatus status, long expires) {
        return new PointRequestItem("request", 1, "One", 2, "Two", 1, BigDecimal.TEN, "help", status, 100, expires, null);
    }

    private static RoomSnapshot room(String status, long id) {
        return new RoomSnapshot(id, "R" + id, "Bàn", status, 1, 2, 8, BigDecimal.TEN,
                List.of(new RoomSnapshot.PlayerSlot(1, "One", 1, false, "PLAYING"),
                        new RoomSnapshot.PlayerSlot(2, "Two", 2, false, "PLAYING")), 1);
    }
}
