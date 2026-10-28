package com.game3cay.server.game;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.server.bus.*;
import com.game3cay.server.room.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoomSafetyTest {
  @TempDir Path directory;

  static class FakeUsers implements UserBUS {
    final Map<Long, UserDTO> users = new ConcurrentHashMap<>();
    volatile long blockedProfileId = -1;
    final CountDownLatch readingProfile = new CountDownLatch(1);
    final CountDownLatch releaseProfile = new CountDownLatch(1);

    UserDTO add(long id, int balance) {
      var user = new UserDTO(id, "p" + id, "P" + id, null, null,
          BigDecimal.valueOf(balance).setScale(2), "ACTIVE", 0, 0, 0);
      users.put(id, user);
      return user;
    }
    public UserDTO getProfile(long id) throws BUSException {
      if (id == blockedProfileId) {
        readingProfile.countDown();
        try {
          if (!releaseProfile.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Profile timeout");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
      }
      return users.get(id);
    }
    public void adjustBalanceForAdmin(long a, long id, BigDecimal d, String r) {
      users.compute(id, (key, u) -> new UserDTO(u.userId(), u.username(), u.displayName(),
          u.email(), u.avatarUrl(), u.balance().add(d), u.status(), u.role(), 0, 0, 0));
    }
    public UserDTO register(String a,String b,String c,String d) { throw new UnsupportedOperationException(); }
    public UserDTO authenticate(String a,String b) { throw new UnsupportedOperationException(); }
    public void updateProfile(long a,String b,String c,String d) { throw new UnsupportedOperationException(); }
    public void changePassword(long a,String b,String c) { throw new UnsupportedOperationException(); }
    public PageResult<AdminUserItem> findUsersForAdmin(String a,String b,int c,int d) { throw new UnsupportedOperationException(); }
    public void updateUserStatusForAdmin(long a,boolean b) { throw new UnsupportedOperationException(); }
    public AdminStatsSnapshot getSystemStatsForAdmin(long a,int b) { throw new UnsupportedOperationException(); }
    public PageResult<AdminLedgerItem> getLedgerForAdmin(int a,int b) { throw new UnsupportedOperationException(); }
  }

  static class RecordingGames implements GameBUS {
    final List<CompletedRound> saved = new CopyOnWriteArrayList<>();
    final AtomicBoolean offline = new AtomicBoolean();
    public void saveCompleted(CompletedRound r) throws BUSException {
      if (offline.get()) throw new BUSException(ErrorCode.INTERNAL_ERROR, "offline");
      saved.add(r);
    }
    public PageResult<GameHistoryItem> getHistory(long a,int b,int c) { throw new UnsupportedOperationException(); }
    public PageResult<LeaderboardItem> getLeaderboard(int a,int b) { throw new UnsupportedOperationException(); }
  }

  static class Events implements RoomEvents {
    final Map<Long, RoomSnapshot> rooms = new ConcurrentHashMap<>();
    final List<Packet> packets = new CopyOnWriteArrayList<>();
    public void roomUpdated(RoomSnapshot r,List<Long> ids) { rooms.put(r.roomId(), r); }
    public void chatMessage(long id,ChatMessage m,List<Long> ids) {}
    public void packet(Packet p,List<Long> ids) { packets.add(p); }
  }

  private void awaitSaved(ResultPersistence p,long user) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (p.pendingFor(user) && System.nanoTime() < until) Thread.sleep(10);
    assertFalse(p.pendingFor(user));
  }

  @Test
  void liveResetPersistsCancellationReleasesOnlyItsParticipantsAndAllowsNextRound() throws Exception {
    var users = new FakeUsers();
    var games = new RecordingGames();
    var events = new Events();
    try (var spool = new ResultPersistence(games, directory);
        var rooms = new RoomManager(events, users, spool)) {
      for (long id : List.of(2L, 3L, 21L, 22L)) {
        rooms.join(users.add(id, 1000), new JoinRoomRequest(id < 20 ? 1 : 2));
      }
      rooms.start(2);
      rooms.start(21);
      rooms.forceResetRoom(2, "test");
      assertEquals("WAITING", events.rooms.get(2L).status());
      assertEquals(ErrorCode.INVALID_STATE,
          assertThrows(BUSException.class, () -> rooms.adjustBalanceForAdmin(999, 2, BigDecimal.ONE, "test")).getErrorCode());
      awaitSaved(spool, 21);
      assertEquals("CANCELLED", games.saved.getFirst().result().roundStatus());
      assertTrue(games.saved.getFirst().result().playerResults().stream().allMatch(p -> p.profit().signum() == 0));
      rooms.setReady(21, true);
      rooms.setReady(22, true);
      rooms.start(21);
      assertEquals("PLAYING", events.rooms.get(2L).status());
    }
  }

  @Test
  void spectatorCanLeaveAndDisconnectDuringPlay() throws Exception {
    var users = new FakeUsers();
    try (var spool = new ResultPersistence(new RecordingGames(), directory);
        var rooms = new RoomManager(new Events(), users, spool)) {
      rooms.join(users.add(21,1000), new JoinRoomRequest(1));
      rooms.join(users.add(22,1000), new JoinRoomRequest(1));
      rooms.join(users.add(99,0), new JoinRoomRequest(1));
      rooms.join(users.add(100,0), new JoinRoomRequest(1));
      rooms.start(21);
      rooms.leave(99);
      rooms.onDisconnected(100);
      assertFalse(rooms.inRoom(99));
      assertFalse(rooms.inRoom(100));
      assertEquals(2, rooms.listRooms().getFirst().playerCount());
    }
  }

  @Test
  void countdownRequiresEveryEligiblePlayerReady() throws Exception {
    var users = new FakeUsers();
    var events = new Events();
    var clock = new AtomicLong();
    try (var spool = new ResultPersistence(new RecordingGames(), directory);
        var rooms = new RoomManager(events, users, spool, clock::get)) {
      for (long id = 1; id <= 3; id++) rooms.join(users.add(id,1000),new JoinRoomRequest(1));
      rooms.setReady(3,false);
      assertEquals(0, events.rooms.get(1L).countdownSeconds());
      clock.set(TimeUnit.SECONDS.toNanos(20));
      rooms.tick();
      assertEquals("WAITING", rooms.listRooms().getFirst().status());
      assertEquals(ErrorCode.NOT_ALL_READY, assertThrows(BUSException.class, () -> rooms.checkStart(1)).getErrorCode());
      assertEquals(RoomManager.AUTO_START_SECONDS, rooms.setReady(3,true).countdownSeconds());
    }
  }

  @Test
  void adminAdjustmentCannotRaceStartSnapshotOrPendingSettlement() throws Exception {
    var users = new FakeUsers();
    var games = new RecordingGames();
    games.offline.set(true);
    try (var spool = new ResultPersistence(games, directory);
        var rooms = new RoomManager(new Events(), users, spool);
        var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
      rooms.join(users.add(21,1000),new JoinRoomRequest(1));
      rooms.join(users.add(22,1000),new JoinRoomRequest(1));
      users.blockedProfileId = 21;
      var start = tasks.submit(() -> { rooms.start(21); return null; });
      assertTrue(users.readingProfile.await(3,TimeUnit.SECONDS));
      assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class,
          () -> rooms.adjustBalanceForAdmin(999,21,new BigDecimal("-1000.00"),"test")).getErrorCode());
      // A slow DB snapshot must not hold the global monitor.
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> rooms.join(users.add(31,1000),new JoinRoomRequest(2)));
      users.releaseProfile.countDown();
      start.get(3,TimeUnit.SECONDS);
      rooms.forceResetRoom(1,"cancel");
      assertTrue(spool.pendingFor(21));
      assertEquals(ErrorCode.INVALID_STATE, assertThrows(BUSException.class,
          () -> rooms.adjustBalanceForAdmin(999,21,BigDecimal.ONE,"test")).getErrorCode());
      games.offline.set(false);
      awaitSaved(spool,21);
      rooms.adjustBalanceForAdmin(999,21,BigDecimal.ONE,"test");
      assertEquals(new BigDecimal("1001.00"),users.getProfile(21).balance());
    }
  }
}
