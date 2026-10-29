package com.game3cay.controller;

import com.game3cay.shared.model.*;
import com.game3cay.shared.network.PacketType;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointOperationJournalTest {
    @Test void reconnectAndRelaunchRestoreTheExactTransferRatherThanCreatingANewOperationId() throws Exception {
        Preferences storage = Preferences.userNodeForPackage(getClass()).node("test-" + UUID.randomUUID());
        try {
            PointOperationJournal journal = new PointOperationJournal(storage);
            String id = UUID.randomUUID().toString();
            PointTransferRequest request = new PointTransferRequest(id, 42, new BigDecimal("123.45"), "Bạn nhận nhé \n=");
            journal.save(new PointOperationJournal.Operation(PacketType.POINT_TRANSFER, request));
            PointOperationJournal restored = new PointOperationJournal(storage);
            var retry = restored.load().orElseThrow();
            assertEquals(PacketType.POINT_TRANSFER, retry.type());
            assertEquals(id, retry.operationId());
            assertEquals(request, retry.data());
            restored.clear();
            assertTrue(journal.load().isEmpty());
        } finally { storage.removeNode(); }
    }

    @Test void requestAcceptanceRetainsBothRequestAndOperationIdAcrossRestart() throws Exception {
        Preferences storage = Preferences.userNodeForPackage(getClass()).node("test-" + UUID.randomUUID());
        try {
            PointOperationJournal journal = new PointOperationJournal(storage);
            PointRequestReply request = new PointRequestReply(UUID.randomUUID().toString(), UUID.randomUUID().toString(), PointRequestDecision.ACCEPT);
            journal.save(new PointOperationJournal.Operation(PacketType.POINT_REQUEST_REPLY, request));
            assertEquals(request, new PointOperationJournal(storage).load().orElseThrow().data());
            journal.ignoredUsers(Set.of(12L, 19L));
            assertEquals(Set.of(12L, 19L), new PointOperationJournal(storage).ignoredUsers());
        } finally { storage.removeNode(); }
    }

    @Test void malformedUnresolvedJournalIsRetainedForVerificationInsteadOfBeingSilentlyReplaced() throws Exception {
        Preferences storage = Preferences.userNodeForPackage(getClass()).node("test-" + UUID.randomUUID());
        try {
            storage.put("pending", "type=POINT_TRANSFER\noperation=invalid\n");
            assertThrows(IllegalStateException.class, () -> new PointOperationJournal(storage).load());
            assertNotNull(storage.get("pending", null));
        } finally { storage.removeNode(); }
    }
}
