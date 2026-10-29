package com.game3cay.server.bus;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.ErrorCode;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointTransferTest {
    ConnectionProvider connections;
    JdbcPointTransferBUS points;
    AtomicLong now = new AtomicLong(1_800_000_000_000L);

    @BeforeEach void database() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        connections = () -> DriverManager.getConnection(url);
        try (var conn = connections.open(); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(50),display_name VARCHAR(100),balance DECIMAL(15,2),role VARCHAR(20),status VARCHAR(20),total_games INT)");
            sql.execute("INSERT INTO users VALUES (1,'one','One',1000,'USER','ACTIVE',3),(2,'two','Two',1000,'USER','ACTIVE',3),(3,'three','Three',1000,'USER','ACTIVE',3),(4,'four','Four',1000,'USER','ACTIVE',3)");
            PointTestSchema.create(conn);
        }
        points = new JdbcPointTransferBUS(connections,
                new PointTransferPolicy(3, new BigDecimal("2000.00"), new BigDecimal("5000.00"), 120, 3), now::get);
    }

    @Test void directTransferIsZeroSumAuditedAndDurablyIdempotent() throws Exception {
        var request = new PointTransferRequest(id(), 2, new BigDecimal("100"), "  Cùng chơi nhé  ");
        var sent = points.transfer(1, request, 7);
        assertTrue(sent.created());
        assertEquals(new BigDecimal("900.00"), balance(1));
        assertEquals(new BigDecimal("1100.00"), balance(2));
        assertEquals(new BigDecimal("2000.00"), balance(1).add(balance(2)));
        assertEquals(2, count("point_transfer_entries"));
        execute("UPDATE users SET balance=0,total_games=0,status='BLOCKED' WHERE id=1");
        var retry = new JdbcPointTransferBUS(connections).transfer(1, request, 99);
        assertFalse(retry.created());
        assertEquals(sent.receipt(), retry.receipt());
        assertEquals(1, count("point_transfers"));
        assertEquals(sent.receipt(), points.findTransfer(1, request.operationId()).orElseThrow());
        assertEquals(sent.receipt(), points.getTransfer(2, sent.receipt().transferId()));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BUSException.class,
                () -> points.getTransfer(3, sent.receipt().transferId())).getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(BUSException.class,
                () -> points.transfer(1, new PointTransferRequest(request.operationId(), 2, new BigDecimal("101"), request.message()), 7)).getErrorCode());
    }

    @Test void transferValidationAndEligibilityLeaveAllBalancesUnchanged() throws Exception {
        for (BigDecimal invalid : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.001"), new BigDecimal("1E+2"), new BigDecimal("5001"))) {
            assertThrows(BUSException.class, () -> points.transfer(1, new PointTransferRequest(id(), 2, invalid, ""), 1));
        }
        assertThrows(BUSException.class, () -> points.transfer(1, new PointTransferRequest(id(), 1, BigDecimal.ONE, ""), 1));
        assertThrows(BUSException.class, () -> points.transfer(1, new PointTransferRequest("not-uuid", 2, BigDecimal.ONE, ""), 1));
        assertThrows(BUSException.class, () -> points.transfer(1, new PointTransferRequest(id(), 2, BigDecimal.ONE, "x".repeat(121)), 1));
        execute("UPDATE users SET total_games=2 WHERE id=1");
        assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class, () -> give(1, 2, "1")).getErrorCode());
        execute("UPDATE users SET total_games=3 WHERE id=1");
        execute("UPDATE users SET status='BLOCKED' WHERE id=2");
        assertEquals(ErrorCode.USER_BLOCKED, assertThrows(BUSException.class, () -> give(1, 2, "1")).getErrorCode());
        execute("UPDATE users SET status='ACTIVE',role='ADMIN' WHERE id=2");
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BUSException.class, () -> give(1, 2, "1")).getErrorCode());
        execute("UPDATE users SET role='USER',balance=9999999999999.99 WHERE id=2");
        assertThrows(BUSException.class, () -> give(1, 2, "1"));
        assertEquals(new BigDecimal("1000.00"), balance(1));
        assertEquals(0, count("point_transfers"));
    }

    @Test void rollingDailyCapCannotBeBypassedAndExpiresAtBoundary() throws Exception {
        execute("UPDATE users SET balance=5000 WHERE id=1");
        give(1, 2, "1200"); give(1, 3, "800");
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, assertThrows(BUSException.class, () -> give(1, 2, "0.01")).getErrorCode());
        now.addAndGet(86_400_000L);
        assertTrue(give(1, 2, "1").created());
    }

    @Test void failureAfterFirstLedgerEntryRollsBackReceiptBalancesAndBothEntries() throws Exception {
        execute("ALTER TABLE point_transfer_entries ADD CONSTRAINT injected_failure CHECK(delta<0)");
        assertThrows(BUSException.class, () -> give(1, 2, "100"));
        assertEquals(new BigDecimal("1000.00"), balance(1));
        assertEquals(new BigDecimal("1000.00"), balance(2));
        assertEquals(0, count("point_transfers")); assertEquals(0, count("point_transfer_entries"));
    }

    @Test void concurrentDebitsCannotOverspendAndOppositeDirectionsAvoidDeadlock() throws Exception {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            Callable<Boolean> debit = () -> { start.await(); try { give(1, 2, "900"); return true; } catch (BUSException e) { return false; } };
            var a = workers.submit(debit); var b = workers.submit(debit); start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("100.00"), balance(1));
            var reverseA = workers.submit(() -> give(1, 2, "50"));
            var reverseB = workers.submit(() -> give(2, 1, "50"));
            reverseA.get(10, TimeUnit.SECONDS); reverseB.get(10, TimeUnit.SECONDS);
            assertEquals(new BigDecimal("2000.00"), balance(1).add(balance(2)));
        }
    }

    @Test void requestLifecycleAuthorizationExpiryAndReplayPersist() throws Exception {
        var input = new PointRequestCreate(id(), 2, new BigDecimal("50"), "Xin ít điểm");
        var request = points.createRequest(1, input, 7);
        assertEquals(request, points.createRequest(1, input, 9));
        assertEquals(request, points.findCreatedRequest(1, input.operationId()).orElseThrow());
        assertThrows(BUSException.class, () -> points.createRequest(2, new PointRequestCreate(id(), 1, BigDecimal.ONE, ""), 7));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BUSException.class, () -> points.getRequest(3, request.requestId())).getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BUSException.class, () -> points.reply(1, reply(request, PointRequestDecision.ACCEPT))).getErrorCode());
        var declined = points.reply(2, reply(request, PointRequestDecision.DECLINE));
        assertEquals(PointRequestStatus.DECLINED, declined.request().status()); assertNull(declined.transfer());
        assertEquals(PointRequestStatus.DECLINED, points.reply(2, reply(request, PointRequestDecision.DECLINE)).request().status());
        var expiring = ask(1, 2, "25"); now.addAndGet(120_000);
        assertEquals(PointRequestStatus.EXPIRED, points.getRequest(2, expiring.requestId()).status());
        assertThrows(BUSException.class, () -> points.reply(2, reply(expiring, PointRequestDecision.ACCEPT)));
        try (var c = connections.open(); var s = c.prepareStatement("SELECT status FROM point_requests WHERE request_id=?")) {
            s.setString(1, expiring.requestId()); try (var rows = s.executeQuery()) { rows.next(); assertEquals("EXPIRED", rows.getString(1)); }
        }
        assertEquals(2, points.listRequests(1, new PageRequest(0, 20)).totalItems());
        assertEquals(0, count("point_transfers"));
    }

    @Test void acceptingRequestTwiceTransfersOnlyOnceAndRecordsBothHistories() throws Exception {
        var request = ask(1, 2, "125");
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = workers.submit(() -> points.reply(2, reply(request, PointRequestDecision.ACCEPT)));
            var b = workers.submit(() -> points.reply(2, reply(request, PointRequestDecision.ACCEPT)));
            var first = a.get(10, TimeUnit.SECONDS); var second = b.get(10, TimeUnit.SECONDS);
            assertNotEquals(first.transfer().created(), second.transfer().created());
            assertEquals(first.transfer().receipt(), second.transfer().receipt());
        }
        assertEquals(new BigDecimal("1125.00"), balance(1)); assertEquals(new BigDecimal("875.00"), balance(2));
        assertEquals(PointRequestStatus.FULFILLED, points.getRequest(1, request.requestId()).status());
        assertEquals(1, points.history(1, new PageRequest(0, 20)).totalItems());
        assertEquals(1, points.history(2, new PageRequest(0, 20)).totalItems());
        assertEquals(0, points.history(3, new PageRequest(0, 20)).totalItems());
        assertEquals(1, count("point_transfers")); assertEquals(2, count("point_transfer_entries"));
    }

    @Test void acceptCancelRaceHasSingleTerminalOutcomeAndNoPartialMoneyMovement() throws Exception {
        var request = ask(1, 2, "80");
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var accept = workers.submit(() -> { start.await(); try { points.reply(2, reply(request, PointRequestDecision.ACCEPT)); return true; } catch (BUSException e) { return false; } });
            var cancel = workers.submit(() -> { start.await(); try { points.reply(1, reply(request, PointRequestDecision.CANCEL)); return true; } catch (BUSException e) { return false; } });
            start.countDown(); assertNotEquals(accept.get(10, TimeUnit.SECONDS), cancel.get(10, TimeUnit.SECONDS));
        }
        var result = points.getRequest(1, request.requestId());
        assertTrue(result.status() == PointRequestStatus.CANCELLED || result.status() == PointRequestStatus.FULFILLED);
        assertEquals(new BigDecimal("2000.00"), balance(1).add(balance(2)));
        assertEquals(result.status() == PointRequestStatus.FULFILLED ? 1 : 0, count("point_transfers"));
    }

    @Test void requestSpamLimitPersistsAcrossServiceInstancesAndCancelDoesNotResetIt() throws Exception {
        for (int target = 2; target <= 4; target++) {
            var request = ask(1, target, "10"); points.reply(1, reply(request, PointRequestDecision.CANCEL));
        }
        var restarted = new JdbcPointTransferBUS(connections,
                new PointTransferPolicy(3, new BigDecimal("2000"), new BigDecimal("5000"), 120, 3), now::get);
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, assertThrows(BUSException.class,
                () -> restarted.createRequest(1, new PointRequestCreate(id(), 2, BigDecimal.ONE, ""), 7)).getErrorCode());
        now.addAndGet(60_001); assertNotNull(ask(1, 2, "10"));
    }

    @Test void acceptOldRequestEnforcesCurrentTransferLimitAfterConfigurationChange() throws Exception {
        var request = ask(1, 2, "250");
        var stricter = new JdbcPointTransferBUS(connections,
                new PointTransferPolicy(3, new BigDecimal("2000"), new BigDecimal("100"), 120, 3), now::get);
        assertEquals(ErrorCode.INVALID_BET_AMOUNT, assertThrows(BUSException.class,
                () -> stricter.reply(2, reply(request, PointRequestDecision.ACCEPT))).getErrorCode());
        assertEquals(PointRequestStatus.PENDING, points.getRequest(1, request.requestId()).status());
        assertEquals(new BigDecimal("1000.00"), balance(1));
        assertEquals(new BigDecimal("1000.00"), balance(2));
        assertEquals(0, count("point_transfers"));
    }

    @Test void pendingRequestsAreListedBeforeNewerTerminalRequests() throws Exception {
        var pending = ask(1, 2, "10"); now.incrementAndGet();
        var declined = ask(1, 3, "10"); points.reply(3, reply(declined, PointRequestDecision.DECLINE));
        assertEquals(pending.requestId(), points.listRequests(1, new PageRequest(0, 1)).items().getFirst().requestId());
    }

    @Test void transferSpamLimitCoversDirectAndAcceptedGiftsPersistsAndIgnoresReplays() throws Exception {
        var first = new PointTransferRequest(id(), 2, new BigDecimal("0.01"), "tiny gift");
        points.transfer(1, first, 7);
        for (int i = 1; i < 30; i++) give(1, 2, "0.01");
        var restarted = new JdbcPointTransferBUS(connections,
                new PointTransferPolicy(3, new BigDecimal("2000"), new BigDecimal("5000"), 120, 3), now::get);
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED,
                assertThrows(BUSException.class, () -> restarted.transfer(1,
                        new PointTransferRequest(id(), 2, new BigDecimal("0.01"), "new tiny gift"), 7)).getErrorCode());
        assertFalse(restarted.transfer(1, first, 7).created());
        var request = ask(2, 1, "0.01");
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, assertThrows(BUSException.class,
                () -> restarted.reply(1, reply(request, PointRequestDecision.ACCEPT))).getErrorCode());
        assertEquals(PointRequestStatus.PENDING, points.getRequest(1, request.requestId()).status());
        assertEquals(30, count("point_transfers"));
        now.addAndGet(60_001);
        assertTrue(restarted.reply(1, reply(request, PointRequestDecision.ACCEPT)).transfer().created());
        assertEquals(31, count("point_transfers"));
        assertEquals(new BigDecimal("2000.00"), balance(1).add(balance(2)));
    }

    private PointTransferOutcome give(long sender, long recipient, String amount) throws Exception {
        return points.transfer(sender, new PointTransferRequest(id(), recipient, new BigDecimal(amount), "test"), 7);
    }
    private PointRequestItem ask(long requester, long target, String amount) throws Exception {
        return points.createRequest(requester, new PointRequestCreate(id(), target, new BigDecimal(amount), "test"), 7);
    }
    private static PointRequestReply reply(PointRequestItem request, PointRequestDecision action) { return new PointRequestReply(id(), request.requestId(), action); }
    private static String id() { return UUID.randomUUID().toString(); }
    private BigDecimal balance(long user) throws Exception {
        try (var c = connections.open(); var q = c.prepareStatement("SELECT balance FROM users WHERE id=?")) {
            q.setLong(1, user); try (var rows = q.executeQuery()) { rows.next(); return rows.getBigDecimal(1); }
        }
    }
    private long count(String table) throws Exception { try (var c = connections.open(); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM " + table)) { r.next(); return r.getLong(1); } }
    private void execute(String sql) throws Exception { try (var c = connections.open(); var s = c.createStatement()) { s.execute(sql); } }
}
