package com.game3cay.server.game;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.server.bus.BUSException;
import com.game3cay.server.bus.UserDTO;
import com.game3cay.server.room.RoomManager;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.GameActionRequest;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.ProfileResult;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.PacketType;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminRoomInteractionTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staleReadyConfirmationCannotUndoResetOrConfirmAChangedBet(boolean changeBet) throws Exception {
        var users = new RoomSafetyTest.FakeUsers();
        var events = new RoomSafetyTest.Events();
        try (var rooms = new RoomManager(events, users, null);
             var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            rooms.join(users.add(1, 1000), new JoinRoomRequest(1));
            rooms.join(users.add(2, 1000), new JoinRoomRequest(1));
            rooms.setReady(2, false);
            users.blockedProfileId = 2;
            var ready = tasks.submit(() -> {
                try {
                    rooms.setReady(2, true);
                    return null;
                } catch (BUSException e) {
                    return e.getErrorCode();
                }
            });
            assertTrue(users.readingProfile.await(3, TimeUnit.SECONDS));
            try {
                if (changeBet) rooms.changeBet(1, new BigDecimal("50.00"));
                else rooms.forceResetRoom(1, "admin reset");
            } finally {
                users.releaseProfile.countDown();
            }
            assertEquals(ErrorCode.INVALID_STATE, ready.get(3, TimeUnit.SECONDS));
            assertTrue(events.rooms.get(1L).players().stream().noneMatch(p -> p.ready()));
            assertEquals(0, events.rooms.get(1L).countdownSeconds());
            assertTrue(rooms.setReady(2, true).players().stream()
                    .anyMatch(p -> p.userId() == 2 && p.ready()));
        }
    }

    @Test
    void committedAdjustmentDoesNotNeedAnotherDatabaseReadToReportSuccess() throws Exception {
        var users = new RoomSafetyTest.FakeUsers() {
            @Override public UserDTO getProfile(long userId) throws BUSException {
                throw new BUSException(ErrorCode.INTERNAL_ERROR, "extra profile read unavailable");
            }
        };
        var events = new RoomSafetyTest.Events();
        try (var rooms = new RoomManager(events, users, null)) {
            rooms.join(users.add(1, 1000), new JoinRoomRequest(1));
            rooms.adjustBalanceForAdmin(999, 1, new BigDecimal("25.00"), "adjustment");
            assertEquals(new BigDecimal("1025.00"), users.users.get(1L).balance());
            assertFalse(events.rooms.get(1L).players().getFirst().ready());
            var update = events.packets.stream().filter(p -> p.getType() == PacketType.PROFILE_RESULT)
                    .map(p -> (ProfileResult) p.getData()).findFirst().orElseThrow();
            assertEquals(1, update.userId());
            assertEquals(new BigDecimal("1025.00"), update.balance());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void freshlyBlockedUserCannotConfirmReadyOrEnterANewRound(boolean startRound) throws Exception {
        var users = new RoomSafetyTest.FakeUsers();
        var events = new RoomSafetyTest.Events();
        try (var persistence = new ResultPersistence(new RoomSafetyTest.RecordingGames(), directory);
             var rooms = new RoomManager(events, users, persistence)) {
            rooms.join(users.add(1, 1000), new JoinRoomRequest(1));
            var target = users.add(2, 1000);
            rooms.join(target, new JoinRoomRequest(1));
            users.users.put(2L, new UserDTO(target.userId(), target.username(), target.displayName(),
                    target.email(), target.avatarUrl(), target.balance(), "BLOCKED", target.role(),
                    target.totalGames(), target.totalWins(), target.totalLosses()));
            BUSException rejected = assertThrows(BUSException.class, () -> {
                if (startRound) rooms.start(1);
                else rooms.setReady(2, true);
            });
            assertEquals(ErrorCode.USER_BLOCKED, rejected.getErrorCode());
            assertEquals("WAITING", rooms.listRooms().getFirst().status());
            assertTrue(events.packets.stream().noneMatch(p -> p.getType() == PacketType.DEAL_CARD));
        }
    }

    @Test
    void liveInspectorBalancesTrackTheSameAnteAndRaiseAsThePlayers() throws Exception {
        var users = new RoomSafetyTest.FakeUsers();
        try (var persistence = new ResultPersistence(new RoomSafetyTest.RecordingGames(), directory);
             var rooms = new RoomManager(new RoomSafetyTest.Events(), users, persistence)) {
            rooms.join(users.add(1, 1000), new JoinRoomRequest(1));
            rooms.join(users.add(2, 1000), new JoinRoomRequest(1));
            rooms.join(users.add(3, 0), new JoinRoomRequest(1));
            rooms.start(1);
            var initial = rooms.getAdminRoomLiveDetail(1);
            assertTrue(initial.players().stream().filter(p -> p.userId() != 3)
                    .allMatch(p -> p.balance().compareTo(new BigDecimal("990.00")) == 0));
            assertEquals(BigDecimal.ZERO, initial.players().stream()
                    .filter(p -> p.userId() == 3).findFirst().orElseThrow().balance().stripTrailingZeros());
            long raiser = initial.currentTurnUserId();
            rooms.action(raiser, new GameActionRequest(initial.roundId(), BetAction.RAISE,
                    new BigDecimal("100.00")));
            var afterRaise = rooms.getAdminRoomLiveDetail(1);
            var player = afterRaise.players().stream().filter(p -> p.userId() == raiser).findFirst().orElseThrow();
            assertEquals(new BigDecimal("900.00"), player.balance());
            assertEquals(new BigDecimal("100.00"), player.currentBet());
            assertEquals(new BigDecimal("110.00"), afterRaise.currentPot());
        }
    }
}
