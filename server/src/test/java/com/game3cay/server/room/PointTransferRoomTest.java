package com.game3cay.server.room;

import com.game3cay.server.bus.*;
import com.game3cay.server.game.CompletedRound;
import com.game3cay.server.game.ResultPersistence;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PointTransferRoomTest {
    @TempDir Path journal;

    @Test void reservesBothBalancesWithoutBlockingOtherTablesThenPushesPrivateBalancesBeforeRelease() throws Exception {
        Users users = new Users();
        Events events = new Events();
        try (var rooms = new RoomManager(events, users, null);
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            joinPair(rooms, users);
            CountDownLatch inDatabase = new CountDownLatch(1);
            CountDownLatch finishDatabase = new CountDownLatch(1);
            events.beforePacket = packet -> {
                if (packet.getType() == PacketType.BALANCE_UPDATED) {
                    assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class, () -> rooms.checkStart(1)).getErrorCode());
                }
            };
            Future<PointTransferReceipt> transfer = workers.submit(() -> rooms.transferPoints(1, 2, 1, roomId -> {
                assertEquals(1, roomId);
                inDatabase.countDown();
                await(finishDatabase);
                users.balance(1, "900.00");
                users.balance(2, "1100.00");
                return outcome(true, "900.00", "1100.00");
            }));
            try {
                assertTrue(inDatabase.await(3, TimeUnit.SECONDS));
                assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class, () -> rooms.setReady(1, true)).getErrorCode());
                assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class, () -> rooms.setReady(2, true)).getErrorCode());
                assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class,
                        () -> rooms.adjustBalanceForAdmin(99, 2, BigDecimal.ONE, "test")).getErrorCode());
                assertThrows(BUSException.class, () -> rooms.transferPoints(2, 1, 1, id -> { fail("Must not execute second DB transaction"); return null; }));
                assertTimeoutPreemptively(Duration.ofSeconds(1), () -> rooms.join(users.add(3, "1000.00"), new JoinRoomRequest(2)));
                assertEquals(0, events.latest.get(1L).countdownSeconds());
            } finally { finishDatabase.countDown(); }
            assertEquals(1, transfer.get(3, TimeUnit.SECONDS).senderId());
            RoomSnapshot snapshot = events.latest.get(1L);
            assertTrue(snapshot.players().stream().noneMatch(RoomSnapshot.PlayerSlot::ready));
            assertEquals(0, snapshot.countdownSeconds());
            var balances = events.sent.stream().filter(sent -> sent.packet().getType() == PacketType.BALANCE_UPDATED).toList();
            assertEquals(2, balances.size());
            for (Sent sent : balances) {
                PointBalanceUpdate update = (PointBalanceUpdate) sent.packet().getData();
                assertEquals(List.of(update.userId()), sent.recipients());
                assertFalse(sent.recipients().contains(3L));
            }
            assertEquals(new BigDecimal("900.00"), liveBalance(rooms, 1));
            assertEquals(new BigDecimal("1100.00"), liveBalance(rooms, 2));
            assertEquals(1, rooms.sharedWaitingRoom(1, 2));
        }
    }

    @Test void failedTransactionReleasesBothReservationsWithoutChangingBalancesOrReadiness() throws Exception {
        Users users = new Users();
        Events events = new Events();
        try (var rooms = new RoomManager(events, users, null)) {
            joinPair(rooms, users);
            BUSException failure = assertThrows(BUSException.class, () -> rooms.transferPoints(1, 2, 1, roomId -> {
                throw new BUSException(ErrorCode.INVALID_BET_AMOUNT, "transaction rolled back");
            }));
            assertEquals(ErrorCode.INVALID_BET_AMOUNT, failure.getErrorCode());
            assertEquals(1, rooms.sharedWaitingRoom(1, 2));
            assertTrue(events.latest.get(1L).players().stream().allMatch(RoomSnapshot.PlayerSlot::ready));
            assertEquals(new BigDecimal("1000.00"), liveBalance(rooms, 1));
            assertEquals(new BigDecimal("1000.00"), liveBalance(rooms, 2));
            assertTrue(events.sent.stream().noneMatch(sent -> sent.packet().getType() == PacketType.BALANCE_UPDATED));
            rooms.adjustBalanceForAdmin(99, 1, BigDecimal.ONE, "after rollback");
            rooms.setReady(2, true);
        }
    }

    @Test void durableReplayDoesNotReapplyOldBalanceOrClearReadiness() throws Exception {
        Users users = new Users();
        Events events = new Events();
        try (var rooms = new RoomManager(events, users, null)) {
            joinPair(rooms, users);
            PointTransferReceipt receipt = rooms.transferPoints(1, 2, 1, roomId -> outcome(false, "700.00", "1300.00"));
            assertNotNull(receipt.transferId());
            assertEquals(new BigDecimal("1000.00"), liveBalance(rooms, 1));
            assertEquals(new BigDecimal("1000.00"), liveBalance(rooms, 2));
            assertTrue(events.latest.get(1L).players().stream().allMatch(RoomSnapshot.PlayerSlot::ready));
            assertTrue(events.sent.stream().noneMatch(sent -> sent.packet().getType() == PacketType.BALANCE_UPDATED));
        }
    }

    @Test void staleReadyProfileAndRejoinCannotOverwriteCommittedTransferBalance() throws Exception {
        Users users = new Users();
        try (var rooms = new RoomManager(new Events(), users, null);
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            joinPair(rooms, users);
            UserDTO oldRecipient = users.getProfile(2);
            users.blockProfileId = 2;
            Future<?> ready = workers.submit(() -> { rooms.setReady(2, true); return null; });
            try {
                assertTrue(users.reading.await(3, TimeUnit.SECONDS));
                rooms.transferPoints(1, 2, 1, ignored -> {
                    users.balance(1, "900.00");
                    users.balance(2, "1100.00");
                    return outcome(true, "900.00", "1100.00");
                });
            } finally { users.release.countDown(); }
            ExecutionException failedReady = assertThrows(ExecutionException.class, () -> ready.get(3, TimeUnit.SECONDS));
            assertInstanceOf(BUSException.class, failedReady.getCause());
            assertEquals(ErrorCode.INVALID_STATE, ((BUSException) failedReady.getCause()).getErrorCode());
            assertEquals(new BigDecimal("1100.00"), liveBalance(rooms, 2));
            rooms.join(oldRecipient, new JoinRoomRequest(1));
            assertEquals(new BigDecimal("1100.00"), liveBalance(rooms, 2));
        }
    }

    @Test void requiresDifferentActivePlayersInSameExpectedWaitingRoomBeforeCallingDatabase() throws Exception {
        Users users = new Users();
        try (var rooms = new RoomManager(new Events(), users, null)) {
            joinPair(rooms, users);
            rooms.join(users.add(3, "1000.00"), new JoinRoomRequest(2));
            RoomManager.PointTransferWork never = roomId -> { fail("Invalid pair must not reach JDBC"); return null; };
            assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(BUSException.class,
                    () -> rooms.transferPoints(1, 1, 1, never)).getErrorCode());
            assertEquals(ErrorCode.NOT_IN_ROOM, assertThrows(BUSException.class,
                    () -> rooms.transferPoints(1, 3, 1, never)).getErrorCode());
            assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class,
                    () -> rooms.transferPoints(1, 2, 2, never)).getErrorCode());
            assertEquals(1, rooms.sharedWaitingRoom(1, 2));
        }
    }

    @Test void leavingDuringAuthorizedTransactionIsSafeAndCannotSpendReservedPointsElsewhere() throws Exception {
        Users users = new Users();
        Events events = new Events();
        try (var rooms = new RoomManager(events, users, null);
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            joinPair(rooms, users);
            CountDownLatch inDatabase = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(1);
            Future<?> transfer = workers.submit(() -> rooms.transferPoints(1, 2, 1, roomId -> {
                inDatabase.countDown();
                await(finish);
                users.balance(1, "900.00");
                users.balance(2, "1100.00");
                return outcome(true, "900.00", "1100.00");
            }));
            try {
                assertTrue(inDatabase.await(3, TimeUnit.SECONDS));
                rooms.leave(2);
                assertFalse(rooms.inRoom(2));
                assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class,
                        () -> rooms.join(users.getProfile(2), new JoinRoomRequest(2))).getErrorCode());
            } finally { finish.countDown(); }
            transfer.get(3, TimeUnit.SECONDS);
            assertFalse(rooms.inRoom(2));
            assertEquals(new BigDecimal("900.00"), liveBalance(rooms, 1));
            rooms.join(users.getProfile(2), new JoinRoomRequest(2));
            assertTrue(rooms.inRoom(2));
            var recipient = rooms.getAdminRoomLiveDetail(2).players().getFirst();
            assertEquals(new BigDecimal("1100.00"), recipient.balance());
        }
    }

    @Test void transferCannotRaceStartSnapshotActiveRoundOrPendingSettlement() throws Exception {
        Users users = new Users();
        Games games = new Games();
        games.offline.set(true);
        try (var persistence = new ResultPersistence(games, journal);
             var rooms = new RoomManager(new Events(), users, persistence);
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            joinPair(rooms, users);
            users.blockProfileId = 1;
            Future<?> start = workers.submit(() -> { rooms.start(1); return null; });
            try {
                assertTrue(users.reading.await(3, TimeUnit.SECONDS));
                assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class,
                        () -> rooms.transferPoints(1, 2, 1, ignored -> outcome(true, "900.00", "1100.00"))).getErrorCode());
            } finally { users.release.countDown(); }
            start.get(3, TimeUnit.SECONDS);
            assertThrows(BUSException.class, () -> rooms.sharedWaitingRoom(1, 2));
            rooms.forceResetRoom(1, "test pending transfer restriction");
            assertTrue(persistence.pendingFor(1));
            assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class,
                    () -> rooms.transferPoints(1, 2, 1, ignored -> outcome(true, "900.00", "1100.00"))).getErrorCode());
            games.offline.set(false);
        }
    }

    @Test void requestUpdatesArePrivateToTheTwoParticipants() {
        Events events = new Events();
        try (var rooms = new RoomManager(events)) {
            PointRequestItem item = new PointRequestItem(UUID.randomUUID().toString(), 1, "A", 2, "B", 1,
                    new BigDecimal("100.00"), "xin điểm", PointRequestStatus.PENDING, 1, 120001, null);
            rooms.notifyPointRequest(item);
            assertEquals(1, events.sent.size());
            assertEquals(PacketType.POINT_REQUEST_UPDATED, events.sent.getFirst().packet().getType());
            assertEquals(List.of(1L, 2L), events.sent.getFirst().recipients());
        }
    }

    private static void joinPair(RoomManager rooms, Users users) throws BUSException {
        rooms.join(users.add(1, "1000.00"), new JoinRoomRequest(1));
        rooms.join(users.add(2, "1000.00"), new JoinRoomRequest(1));
    }

    private static BigDecimal liveBalance(RoomManager rooms, long id) throws BUSException {
        return rooms.getAdminRoomLiveDetail(1).players().stream().filter(player -> player.userId() == id).findFirst().orElseThrow().balance();
    }

    private static PointTransferOutcome outcome(boolean created, String sender, String recipient) {
        return new PointTransferOutcome(new PointTransferReceipt(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                null, 1, "P1", 2, "P2", new BigDecimal("100.00"), "test", System.currentTimeMillis()),
                new BigDecimal(sender), new BigDecimal(recipient), created);
    }

    private static void await(CountDownLatch latch) throws BUSException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new BUSException(ErrorCode.INTERNAL_ERROR, "Test gate timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BUSException(ErrorCode.INTERNAL_ERROR, "Test interrupted", e);
        }
    }

    private record Sent(Packet packet, List<Long> recipients) {}
    private static final class Events implements RoomEvents {
        final Map<Long, RoomSnapshot> latest = new ConcurrentHashMap<>();
        final List<Sent> sent = new CopyOnWriteArrayList<>();
        volatile java.util.function.Consumer<Packet> beforePacket = ignored -> {};
        public void roomUpdated(RoomSnapshot room, List<Long> recipients) { latest.put(room.roomId(), room); }
        public void chatMessage(long room, ChatMessage message, List<Long> recipients) {}
        public void packet(Packet packet, List<Long> recipients) {
            beforePacket.accept(packet);
            sent.add(new Sent(packet, List.copyOf(recipients)));
        }
    }

    private static final class Users implements UserBUS {
        final Map<Long, UserDTO> users = new ConcurrentHashMap<>();
        volatile long blockProfileId = -1;
        final CountDownLatch reading = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        UserDTO add(long id, String balance) {
            UserDTO user = new UserDTO(id, "p" + id, "P" + id, null, null, new BigDecimal(balance), "ACTIVE", "USER", 3, 1, 2);
            users.put(id, user);
            return user;
        }
        void balance(long id, String balance) {
            users.compute(id, (key, user) -> new UserDTO(user.userId(), user.username(), user.displayName(), user.email(),
                    user.avatarUrl(), new BigDecimal(balance), user.status(), user.role(), user.totalGames(), user.totalWins(), user.totalLosses()));
        }
        public UserDTO getProfile(long id) throws BUSException {
            UserDTO fetched = users.get(id);
            if (id == blockProfileId) { reading.countDown(); await(release); }
            return fetched;
        }
        public void adjustBalanceForAdmin(long admin, long target, BigDecimal delta, String reason) {
            balance(target, users.get(target).balance().add(delta).toPlainString());
        }
        public UserDTO register(String a, String b, String c, String d) { throw new UnsupportedOperationException(); }
        public UserDTO authenticate(String a, String b) { throw new UnsupportedOperationException(); }
        public void updateProfile(long a, String b, String c, String d) { throw new UnsupportedOperationException(); }
        public void changePassword(long a, String b, String c) { throw new UnsupportedOperationException(); }
        public PageResult<AdminUserItem> findUsersForAdmin(String a, String b, int c, int d) { throw new UnsupportedOperationException(); }
        public void updateUserStatusForAdmin(long a, boolean b) { throw new UnsupportedOperationException(); }
        public AdminStatsSnapshot getSystemStatsForAdmin(long a, int b) { throw new UnsupportedOperationException(); }
        public PageResult<AdminLedgerItem> getLedgerForAdmin(int a, int b) { throw new UnsupportedOperationException(); }
    }

    private static final class Games implements GameBUS {
        final AtomicBoolean offline = new AtomicBoolean();
        public void saveCompleted(CompletedRound round) throws BUSException {
            if (offline.get()) throw new BUSException(ErrorCode.INTERNAL_ERROR, "test DB offline");
        }
        public PageResult<GameHistoryItem> getHistory(long user, int page, int size) { throw new UnsupportedOperationException(); }
        public PageResult<LeaderboardItem> getLeaderboard(int page, int size) { throw new UnsupportedOperationException(); }
    }
}
