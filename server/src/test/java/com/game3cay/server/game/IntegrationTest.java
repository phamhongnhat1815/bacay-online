package com.game3cay.server.game;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.server.bus.*;
import com.game3cay.server.dal.*;
import com.game3cay.server.network.*;
import com.game3cay.server.room.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class IntegrationTest {
  ConnectionProvider cp;
  JdbcUserBUS users;
  JdbcGameBUS games;
  String mysqlDatabase;

  @TempDir Path directory;

  @BeforeEach
  void database() throws Exception {
    String base = System.getenv("BACAY_TEST_MYSQL_URL");

    if (base == null) {
      String url =
          "jdbc:h2:mem:"
              + UUID.randomUUID()
              + ";MODE=MySQL;DB_CLOSE_DELAY=-1";

      cp = () -> DriverManager.getConnection(url);
    } else {
      mysqlDatabase =
          "bacay_test_" + UUID.randomUUID().toString().replace("-", "");

      String username =
          System.getenv().getOrDefault("BACAY_TEST_MYSQL_USER", "root");

      String password =
          System.getenv().getOrDefault("BACAY_TEST_MYSQL_PASSWORD", "");

      try (var c = DriverManager.getConnection(base, username, password);
          var s = c.createStatement()) {
        s.execute(
            "CREATE DATABASE " + mysqlDatabase + " CHARACTER SET utf8mb4");
      }

      cp =
          () ->
              DriverManager.getConnection(
                  base
                      + mysqlDatabase
                      + "?allowPublicKeyRetrieval=true&useSSL=false",
                  username,
                  password);
    }

    users = new JdbcUserBUS(cp);
    games = new JdbcGameBUS(cp);

    try (var c = cp.open();
        var s = c.createStatement()) {
      s.execute(
          "CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY,"
              + "username VARCHAR(50) UNIQUE NOT NULL,"
              + "password_hash VARCHAR(255) NOT NULL,"
              + "display_name VARCHAR(100) NOT NULL,"
              + "email VARCHAR(100) UNIQUE,avatar_url VARCHAR(255),"
              + "balance DECIMAL(15,2) DEFAULT 0 NOT NULL CHECK(balance>=0),"
              + "status VARCHAR(20) DEFAULT 'ACTIVE',"
              + "total_games INT DEFAULT 0,total_wins INT DEFAULT 0,"
              + "total_losses INT DEFAULT 0)");

      s.execute(
          "CREATE TABLE play_rounds(id BIGINT AUTO_INCREMENT PRIMARY KEY,"
              + "round_id VARCHAR(64) UNIQUE,content_hash VARCHAR(64),"
              + "runtime_room_id BIGINT,room_name VARCHAR(100),"
              + "ante DECIMAL(15,2),status VARCHAR(20),ended_at BIGINT)");

      s.execute(
          "CREATE TABLE play_results(round_id VARCHAR(64),"
              + "user_id BIGINT REFERENCES users(id),"
              + "display_name VARCHAR(100),placement INT,"
              + "outcome VARCHAR(20),profit DECIMAL(15,2),"
              + "hand_type VARCHAR(20),score INT,"
              + "PRIMARY KEY(round_id,user_id))");

      s.execute(
          "CREATE TABLE play_cards(round_id VARCHAR(64),user_id BIGINT,"
              + "position_no INT,rank_name VARCHAR(10),suit_name VARCHAR(10),"
              + "PRIMARY KEY(round_id,user_id,position_no),"
              + "UNIQUE(round_id,rank_name,suit_name))");

      s.execute(
          "CREATE TABLE play_bets(round_id VARCHAR(64),sequence_no INT,"
              + "user_id BIGINT,action VARCHAR(20),total_bet DECIMAL(15,2),"
              + "added_amount DECIMAL(15,2),created_at BIGINT,"
              + "PRIMARY KEY(round_id,sequence_no))");

      s.execute(
          "CREATE TABLE play_pots(round_id VARCHAR(64),pot_no INT,"
              + "amount DECIMAL(15,2),PRIMARY KEY(round_id,pot_no))");

      s.execute(
          "CREATE TABLE play_pot_players(round_id VARCHAR(64),"
              + "pot_no INT,user_id BIGINT,"
              + "PRIMARY KEY(round_id,pot_no,user_id))");

      s.execute(
          "CREATE TABLE play_ledger(round_id VARCHAR(64),user_id BIGINT,"
              + "delta DECIMAL(15,2),balance_before DECIMAL(15,2),"
              + "balance_after DECIMAL(15,2),PRIMARY KEY(round_id,user_id),"
              + "CHECK(balance_after>=0),"
              + "CHECK(balance_after=balance_before+delta))");
    }
  }

  @AfterEach
  void cleanup() throws Exception {
    if (mysqlDatabase != null) {
      try (var c = cp.open();
          var s = c.createStatement()) {
        s.execute("DROP DATABASE " + mysqlDatabase);
      }
    }
  }

  List<UserDTO> users(int n) throws Exception {
    var list = new ArrayList<UserDTO>();

    for (int i = 0; i < n; i++) {
      list.add(
          users.register(
              "player_" + i, "password123", "Người " + i, null));
    }

    return list;
  }

  CompletedRound completed(List<UserDTO> people) throws Exception {
    var e = new DefaultGameEngine(new Random(5));
    String id = UUID.randomUUID().toString();

    e.initRound(
        id,
        1,
        people.stream()
            .map(
                u ->
                    new RoundParticipant(
                        u.userId(),
                        u.username(),
                        u.displayName(),
                        u.balance()))
            .toList(),
        new BigDecimal("10.00"));

    var hands =
        people.stream()
            .map(
                u -> {
                  try {
                    return new CompletedRound.PrivateHand(
                        u.userId(), e.getPlayerCards(id, u.userId()));
                  } catch (Exception ex) {
                    throw new RuntimeException(ex);
                  }
                })
            .toList();

    while (e.isRoundActive(id)) {
      e.handleAction(
          id,
          e.getPublicSnapshot(id).currentTurnUserId(),
          BetAction.CALL,
          null);
    }

    return new CompletedRound(
        e.getRoundResult(id),
        1,
        "Phòng test",
        new BigDecimal("10.00"),
        hands);
  }

  long count(String table) throws Exception {
    try (var c = cp.open();
        var s = c.createStatement();
        var rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  @Test
  void savesOnlyOnceAndRetrievesCorrectUser() throws Exception {
    var people = users(3);
    var round = completed(people.subList(0, 2));

    games.saveCompleted(round);
    games.saveCompleted(round);

    assertEquals(1, count("play_rounds"));
    assertEquals(6, count("play_cards"));
    assertEquals(2, count("play_ledger"));

    for (var u : people.subList(0, 2)) {
      assertEquals(1, users.getProfile(u.userId()).totalGames());
    }

    assertEquals(
        1,
        games.getHistory(people.get(0).userId(), 0, 20).totalItems());

    assertEquals(
        0,
        games.getHistory(people.get(2).userId(), 0, 20).totalItems());

    assertEquals(
        0,
        users.getProfile(people.get(0).userId()).balance()
            .add(users.getProfile(people.get(1).userId()).balance())
            .compareTo(new BigDecimal("2000.00")));

    assertThrows(
        BUSException.class, () -> games.getHistory(1, -1, 20));

    assertEquals(3, games.getLeaderboard(0, 20).items().size());

    var changed =
        new CompletedRound(
            round.result(), 1, "Khác phòng", round.ante(), round.hands());

    assertThrows(
        BUSException.class, () -> games.saveCompleted(changed));
  }

  @Test
  void failureHalfWayRollsBackAllTablesAndPoints() throws Exception {
    var people = users(2);
    var good = completed(people);

    var bad =
        new CompletedRound(
            good.result(),
            1,
            good.roomName(),
            good.ante(),
            List.of(
                good.hands().get(0),
                new CompletedRound.PrivateHand(
                    people.get(1).userId(),
                    good.hands().get(0).cards())));

    assertThrows(
        BUSException.class, () -> games.saveCompleted(bad));

    assertEquals(0, count("play_rounds"));
    assertEquals(0, count("play_results"));
    assertEquals(0, count("play_ledger"));

    for (var u : people) {
      assertEquals(0, users.getProfile(u.userId()).totalGames());
      assertEquals(
          new BigDecimal("1000.00"),
          users.getProfile(u.userId()).balance());
    }
  }

  @Test
  void passwordAndProfilePersist() throws Exception {
    var people = users(2);
    var u = people.get(0);

    users.updateProfile(
        u.userId(), "Tên mới", "new@example.com", null);

    assertEquals(
        "Tên mới", users.getProfile(u.userId()).displayName());

    users.updateProfile(u.userId(), null, "", null);
    users.updateProfile(people.get(1).userId(), null, "", null);

    assertNull(users.getProfile(u.userId()).email());
    assertNull(users.getProfile(people.get(1).userId()).email());

    users.changePassword(
        u.userId(), "password123", "newpassword123");

    assertThrows(
        BUSException.class,
        () -> users.authenticate(u.username(), "password123"));

    assertEquals(
        u.userId(),
        users.authenticate(u.username(), "newpassword123").userId());
  }

  @Test
  void journalRecoversAfterRestartAndDbRetry() throws Exception {
    var round = completed(users(2));
    var online = new AtomicBoolean(false);
    var attempts = new CountDownLatch(1);
    var saved = new CountDownLatch(1);

    GameBUS unreliable =
        new GameBUS() {
          public void saveCompleted(CompletedRound r) throws BUSException {
            attempts.countDown();

            if (!online.get()) {
              throw new BUSException(ErrorCode.INTERNAL_ERROR, "offline");
            }

            games.saveCompleted(r);
            saved.countDown();
          }

          public PageResult<GameHistoryItem> getHistory(
              long id, int p, int n) throws BUSException {
            return games.getHistory(id, p, n);
          }

          public PageResult<LeaderboardItem> getLeaderboard(
              int p, int n) throws BUSException {
            return games.getLeaderboard(p, n);
          }
        };

    try (var spool = new ResultPersistence(unreliable, directory)) {
      spool.enqueue(round);

      assertTrue(attempts.await(3, TimeUnit.SECONDS));

      assertTrue(
          spool.pendingFor(
              round.result().playerResults().get(0).userId()));
    }

    online.set(true);

    try (var restarted = new ResultPersistence(unreliable, directory)) {
      assertTrue(saved.await(5, TimeUnit.SECONDS));
    }

    assertEquals(1, count("play_rounds"));

    games.saveCompleted(round);

    assertEquals(
        1,
        users.getProfile(
                round.result().playerResults().get(0).userId())
            .totalGames());
  }

  @Test
  @Timeout(30)
  void fourTcpClientsTwoRoomsGameHistoryAndPrivacy() throws Exception {
    var people = users(4);
    var sessions = new SessionRegistry();

    try (var persistence = new ResultPersistence(games, directory);
        var listener = new ServerSocket(0)) {
      var rooms =
          new RoomManager(
              new RoomEvents() {
                public void roomUpdated(
                    RoomSnapshot r, List<Long> ids) {
                  sessions.broadcast(
                      ids, Packet.serverPush(PacketType.ROOM_UPDATED, r));
                }

                public void chatMessage(
                    long roomId, ChatMessage m, List<Long> ids) {
                  sessions.broadcast(
                      ids,
                      Packet.serverPush(
                          "SYSTEM".equals(m.type())
                              ? PacketType.CHAT_SYSTEM
                              : PacketType.CHAT_MESSAGE,
                          new RoomChatEvent(roomId, m)));
                }

                public void packet(Packet p, List<Long> ids) {
                  sessions.broadcast(ids, p);
                }
              },
              users,
              persistence);

      var handlers = new ArrayList<ClientHandler>();
      var wires = new ArrayList<Wire>();
      var workers = Executors.newFixedThreadPool(4);

      try {
        for (int i = 0; i < 4; i++) {
          var socket =
              new Socket("127.0.0.1", listener.getLocalPort());

          var handler =
              new ClientHandler(
                  listener.accept(), users, sessions, rooms, games);

          handlers.add(handler);
          workers.submit(handler);
          wires.add(new Wire(socket));

          assertEquals(
              PacketType.AUTH_RESULT,
              wires.get(i)
                  .request(
                      PacketType.LOGIN,
                      new AuthRequest(
                          people.get(i).username(), "password123"))
                  .getType());
        }

        var a = wires.get(0);
        var b = wires.get(1);
        var c = wires.get(2);
        var d = wires.get(3);

        var first =
            (RoomSnapshot)
                a.request(
                        PacketType.JOIN_ROOM, new JoinRoomRequest(1))
                    .getData();

        b.request(
            PacketType.JOIN_ROOM,
            new JoinRoomRequest(first.roomId()));

        var second =
            (RoomSnapshot)
                c.request(
                        PacketType.JOIN_ROOM, new JoinRoomRequest(2))
                    .getData();

        d.request(
            PacketType.JOIN_ROOM,
            new JoinRoomRequest(second.roomId()));

        assertEquals(
            ErrorCode.NOT_ROOM_OWNER,
            ((Packet.ErrorPayload)
                    b.request(
                            PacketType.CHANGE_ROOM_BET,
                            new BigDecimal("50.00"))
                        .getData())
                .code());

        var changedBet =
            (RoomSnapshot)
                a.request(
                        PacketType.CHANGE_ROOM_BET,
                        new BigDecimal("50.00"))
                    .getData();

        assertEquals(
            new BigDecimal("50.00"), changedBet.betAmount());

        for (var w : wires) {
          w.request(PacketType.SET_READY, true);
        }

        a.request(
            PacketType.CHAT_MESSAGE,
            new ChatMessage(
                null, null, "Chỉ phòng một", "TEXT", 0));

        a.request(PacketType.START_GAME_REQUEST, null);

        String rid =
            ((GameStartInfo) a.await(PacketType.START_GAME).getData())
                .roundId();

        var sb =
            (GameStateSnapshot)
                b.await(PacketType.GAME_UPDATED).getData();

        assertEquals(
            people.get(1).userId(), sb.currentTurnUserId());

        assertEquals(
            ErrorCode.NOT_YOUR_TURN,
            ((Packet.ErrorPayload)
                    a.request(
                            PacketType.GAME_ACTION,
                            new GameActionRequest(
                                rid, BetAction.CALL, null))
                        .getData())
                .code());

        assertEquals(
            ErrorCode.STALE_ROUND,
            ((Packet.ErrorPayload)
                    b.request(
                            PacketType.GAME_ACTION,
                            new GameActionRequest(
                                "old", BetAction.CALL, null))
                        .getData())
                .code());

        b.request(
            PacketType.GAME_ACTION,
            new GameActionRequest(rid, BetAction.CALL, null));

        a.request(
            PacketType.GAME_ACTION,
            new GameActionRequest(rid, BetAction.CALL, null));

        var end =
            (RoundResult) a.await(PacketType.ROUND_RESULT).getData();

        assertEquals("FINISHED", end.roundStatus());
        b.await(PacketType.ROUND_RESULT);

        var myA =
            (PlayerStateSnapshot)
                a.await(PacketType.PLAYER_STATE).getData();

        var myB =
            (PlayerStateSnapshot)
                b.await(PacketType.PLAYER_STATE).getData();

        assertTrue(
            Collections.disjoint(myA.myCards(), myB.myCards()));

        for (var packet : a.seen) {
          if (packet.getData() instanceof PlayerStateSnapshot p) {
            assertEquals(myA.myCards(), p.myCards());
          }
        }

        c.request(PacketType.ROOM_LIST, null);
        d.request(PacketType.ROOM_LIST, null);

        assertFalse(
            c.seen.stream()
                .anyMatch(
                    p ->
                        p.getType() == PacketType.START_GAME
                            || p.getType() == PacketType.PLAYER_STATE
                            || p.getType() == PacketType.ROUND_RESULT));

        assertFalse(
            c.seen.stream()
                .anyMatch(
                    p ->
                        p.getData() instanceof RoomChatEvent e
                            && "Chỉ phòng một"
                                .equals(e.message().content())));

        long deadline =
            System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (count("play_rounds") == 0
            && System.nanoTime() < deadline) {
          Thread.sleep(20);
        }

        var history =
            (PageResult<?>)
                a.request(
                        PacketType.GET_HISTORY, new PageRequest(0, 20))
                    .getData();

        assertEquals(1, history.totalItems());

        var empty =
            (PageResult<?>)
                c.request(
                        PacketType.GET_HISTORY, new PageRequest(0, 20))
                    .getData();

        assertEquals(0, empty.totalItems());

        // Đóng một trong hai client phải chốt ván như úp bài.
        c.request(PacketType.START_GAME_REQUEST, null);
        c.await(PacketType.START_GAME);
        d.close();

        assertEquals(
            "FINISHED",
            ((RoundResult) c.await(PacketType.ROUND_RESULT).getData())
                .roundStatus());

        long savedUntil =
            System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (persistence.pendingFor(people.get(2).userId())
            && System.nanoTime() < savedUntil) {
          Thread.sleep(10);
        }

        assertFalse(
            persistence.pendingFor(people.get(2).userId()));

        assertEquals(
            new BigDecimal("1010.00"),
            users.getProfile(people.get(2).userId()).balance());

        assertEquals(
            new BigDecimal("990.00"),
            users.getProfile(people.get(3).userId()).balance());

        a.request(PacketType.LEAVE_ROOM, null);
        b.request(PacketType.LEAVE_ROOM, null);

        assertEquals(
            PacketType.PROFILE_RESULT,
            a.request(
                    PacketType.UPDATE_PROFILE,
                    new UpdateProfileRequest("Tên TCP", null, null))
                .getType());

        assertEquals(
            PacketType.ACK,
            a.request(
                    PacketType.CHANGE_PASSWORD,
                    new ChangePasswordRequest(
                        "password123", "newpassword123"))
                .getType());

        a.request(PacketType.LOGOUT, null);

        assertEquals(
            PacketType.AUTH_RESULT,
            a.request(
                    PacketType.LOGIN,
                    new AuthRequest(
                        people.get(0).username(), "newpassword123"))
                .getType());
      } finally {
        for (var w : wires) {
          w.close();
        }

        handlers.forEach(ClientHandler::close);
        workers.shutdownNow();
        workers.awaitTermination(3, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void leavingActiveRoundCannotSpendSamePointsInAnotherRoom()
      throws Exception {
    var people = users(3);

    try (var persistence = new ResultPersistence(games, directory)) {
      var rooms =
          new RoomManager(
              new RoomEvents() {
                public void roomUpdated(
                    RoomSnapshot r, List<Long> ids) {}

                public void chatMessage(
                    long roomId, ChatMessage m, List<Long> ids) {}
              },
              users,
              persistence);

      var room =
          rooms.join(people.get(0), new JoinRoomRequest(1));

      for (var u : people.subList(1, 3)) {
        rooms.join(u, new JoinRoomRequest(room.roomId()));
      }

      for (var u : people) {
        rooms.setReady(u.userId(), true);
      }

      rooms.start(people.get(0).userId());
      rooms.leave(people.get(0).userId());

      assertEquals(
          ErrorCode.INVALID_STATE,
          assertThrows(
                  BUSException.class,
                  () ->
                      rooms.join(
                          people.get(0), new JoinRoomRequest(2)))
              .getErrorCode());

      rooms.onDisconnected(people.get(1).userId());

      assertEquals(
          "RESULT", rooms.listRooms().getFirst().status());
    }
  }

  @Test
  void spectatorsCanLeaveOrDisconnectDuringActiveRound()
      throws Exception {
    var people = users(4);

    for (var u : people.subList(2, 4)) {
      try (var c = cp.open();
          var q =
              c.prepareStatement(
                  "UPDATE users SET balance=0 WHERE id=?")) {
        q.setLong(1, u.userId());
        q.executeUpdate();
      }
    }

    try (var persistence = new ResultPersistence(games, directory)) {
      var rooms =
          new RoomManager(
              new RoomEvents() {
                public void roomUpdated(
                    RoomSnapshot r, List<Long> ids) {}

                public void chatMessage(
                    long id, ChatMessage m, List<Long> ids) {}
              },
              users,
              persistence);

      for (var u : people) {
        rooms.join(
            users.getProfile(u.userId()),
            new JoinRoomRequest(1));
      }

      rooms.setReady(people.get(0).userId(), true);
      rooms.setReady(people.get(1).userId(), true);
      rooms.start(people.get(0).userId());

      long spectator = people.get(2).userId();
      long disconnected = people.get(3).userId();

      rooms.leave(spectator);
      assertFalse(rooms.inRoom(spectator));

      assertEquals(
          2,
          rooms.join(
                  users.getProfile(spectator),
                  new JoinRoomRequest(2))
              .roomId());

      rooms.onDisconnected(disconnected);
      assertFalse(rooms.inRoom(disconnected));

      assertEquals(
          2,
          rooms.join(
                  users.getProfile(disconnected),
                  new JoinRoomRequest(2))
              .roomId());

      assertEquals(
          "PLAYING", rooms.listRooms().getFirst().status());

      assertEquals(
          2, rooms.listRooms().getFirst().playerCount());
    }
  }

  @Test
  void spectatorOwnerDisconnectTransfersOwnershipWithoutCancellingRound()
      throws Exception {
    var people = users(3);
    long owner = people.get(0).userId();

    try (var c = cp.open();
        var q =
            c.prepareStatement(
                "UPDATE users SET balance=0 WHERE id=?")) {
      q.setLong(1, owner);
      q.executeUpdate();
    }

    var latest = new AtomicReference<RoomSnapshot>();

    try (var persistence = new ResultPersistence(games, directory)) {
      var rooms =
          new RoomManager(
              new RoomEvents() {
                public void roomUpdated(
                    RoomSnapshot r, List<Long> ids) {
                  latest.set(r);
                }

                public void chatMessage(
                    long id, ChatMessage m, List<Long> ids) {}
              },
              users,
              persistence);

      for (var u : people) {
        rooms.join(
            users.getProfile(u.userId()),
            new JoinRoomRequest(1));
      }

      rooms.setReady(people.get(1).userId(), true);
      rooms.setReady(people.get(2).userId(), true);
      rooms.start(owner);

      rooms.onDisconnected(owner);

      assertFalse(rooms.inRoom(owner));

      assertEquals(
          people.get(1).userId(),
          latest.get().ownerUserId());

      assertEquals("PLAYING", latest.get().status());
      assertEquals(2, latest.get().players().size());
    }
  }

  @Test
  void betChangeRequiresOwnerResetsReadyAndUpdatesLobby()
      throws Exception {
    var people = users(2);
    var snapshots = new ArrayList<RoomSnapshot>();

    var rooms =
        new RoomManager(
            new RoomEvents() {
              public void roomUpdated(
                  RoomSnapshot r, List<Long> ids) {
                snapshots.add(r);
              }

              public void chatMessage(
                  long id, ChatMessage m, List<Long> ids) {}
            });

    rooms.join(people.get(0), new JoinRoomRequest(1));
    rooms.join(people.get(1), new JoinRoomRequest(1));

    rooms.setReady(people.get(0).userId(), true);
    rooms.setReady(people.get(1).userId(), true);

    assertEquals(
        ErrorCode.NOT_ROOM_OWNER,
        assertThrows(
                BUSException.class,
                () ->
                    rooms.changeBet(
                        people.get(1).userId(),
                        new BigDecimal("50.00")))
            .getErrorCode());

    var updated =
        rooms.changeBet(
            people.get(0).userId(), new BigDecimal("50.00"));

    assertTrue(
        updated.players().stream()
            .noneMatch(RoomSnapshot.PlayerSlot::ready));

    assertEquals(
        new BigDecimal("50.00"),
        rooms.listRooms().getFirst().betAmount());

    assertEquals(updated, snapshots.getLast());

    assertEquals(
        ErrorCode.NOT_ALL_READY,
        assertThrows(
                BUSException.class,
                () -> rooms.checkStart(people.get(0).userId()))
            .getErrorCode());

    for (var bad :
        List.of(
            new BigDecimal("0"),
            new BigDecimal("-1"),
            new BigDecimal("1.001"),
            new BigDecimal("9999999999999999"))) {
      assertEquals(
          ErrorCode.INVALID_BET_AMOUNT,
          assertThrows(
                  BUSException.class,
                  () -> rooms.changeBet(people.get(0).userId(), bad))
              .getErrorCode());
    }

    rooms.leave(people.get(0).userId());

    assertEquals(
        new BigDecimal("50.00"),
        rooms.setReady(people.get(1).userId(), true).betAmount());

    rooms.leave(people.get(1).userId());

    assertEquals(
        new BigDecimal("10.00"),
        rooms.listRooms().getFirst().betAmount());

    assertEquals(20, rooms.listRooms().size());
  }

  @Test
  void resultHoldBlocksReplayAndChangedAnteIsUsedInNextRound()
      throws Exception {
    var people = users(3);
    var clock = new AtomicLong();
    var round = new AtomicReference<String>();
    var snapshots = new ArrayList<RoomSnapshot>();
    var privateStates = new ArrayList<PlayerStateSnapshot>();

    try (var persistence = new ResultPersistence(games, directory)) {
      var rooms =
          new RoomManager(
              new RoomEvents() {
                public void roomUpdated(
                    RoomSnapshot r, List<Long> ids) {
                  snapshots.add(r);
                }

                public void chatMessage(
                    long id, ChatMessage m, List<Long> ids) {}

                public void packet(Packet p, List<Long> ids) {
                  if (p.getData() instanceof GameStartInfo start) {
                    round.set(start.roundId());
                  }

                  if (p.getData() instanceof PlayerStateSnapshot state) {
                    privateStates.add(state);
                  }
                }
              },
              users,
              persistence,
              clock::get);

      long owner = people.get(0).userId();
      long guest = people.get(1).userId();

      rooms.join(people.get(0), new JoinRoomRequest(1));
      rooms.join(people.get(1), new JoinRoomRequest(1));

      rooms.changeBet(owner, new BigDecimal("50.00"));
      rooms.setReady(owner, true);
      rooms.setReady(guest, true);
      rooms.start(owner);

      assertEquals(
          new BigDecimal("50.00"),
          privateStates.getFirst().myCurrentBet());

      assertEquals(
          new BigDecimal("950.00"),
          privateStates.getFirst().myBalance());

      assertEquals(
          ErrorCode.INVALID_STATE,
          assertThrows(
                  BUSException.class,
                  () -> rooms.changeBet(owner, BigDecimal.ONE))
              .getErrorCode());

      rooms.action(
          guest, new GameActionRequest(round.get(), BetAction.CALL, null));

      rooms.action(
          owner, new GameActionRequest(round.get(), BetAction.CALL, null));

      long until =
          System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

      while (persistence.pendingFor(owner)
          && System.nanoTime() < until) {
        Thread.sleep(10);
      }

      assertFalse(persistence.pendingFor(owner));

      assertEquals(
          "RESULT", rooms.listRooms().getFirst().status());

      assertTrue(
          snapshots.getLast().players().stream()
              .noneMatch(RoomSnapshot.PlayerSlot::ready));

      assertEquals(
          ErrorCode.INVALID_STATE,
          assertThrows(
                  BUSException.class,
                  () -> rooms.setReady(owner, true))
              .getErrorCode());

      assertEquals(
          ErrorCode.INVALID_STATE,
          assertThrows(
                  BUSException.class,
                  () -> rooms.start(owner))
              .getErrorCode());

      assertEquals(
          ErrorCode.INVALID_STATE,
          assertThrows(
                  BUSException.class,
                  () -> rooms.changeBet(owner, BigDecimal.ONE))
              .getErrorCode());

      assertEquals(
          ErrorCode.ROOM_NOT_WAITING,
          assertThrows(
                  BUSException.class,
                  () ->
                      rooms.join(
                          people.get(2), new JoinRoomRequest(1)))
              .getErrorCode());

      clock.set(
          TimeUnit.SECONDS.toNanos(RoundResult.DISPLAY_SECONDS) - 1);

      rooms.tick();

      assertEquals(
          "RESULT", rooms.listRooms().getFirst().status());

      clock.incrementAndGet();
      rooms.tick();

      assertEquals(
          "WAITING", rooms.listRooms().getFirst().status());

      rooms.setReady(owner, true);
      rooms.setReady(guest, true);

      assertTrue(
          snapshots.getLast().players().stream()
              .allMatch(RoomSnapshot.PlayerSlot::ready));

      rooms.changeBet(owner, new BigDecimal("100.00"));

      assertTrue(
          snapshots.getLast().players().stream()
              .noneMatch(RoomSnapshot.PlayerSlot::ready));
    }
  }

  static final class Wire implements AutoCloseable {
    final Socket socket;
    final ObjectOutputStream out;
    final ObjectInputStream in;
    final List<Packet> seen = new ArrayList<>();

    Wire(Socket s) throws Exception {
      socket = s;
      s.setSoTimeout(5000);

      out = new ObjectOutputStream(s.getOutputStream());
      out.flush();

      in = PacketIO.openInput(s);
    }

    Packet next() throws Exception {
      while (true) {
        var p = PacketIO.read(in);

        if (p.getType() == PacketType.PING) {
          PacketIO.write(
              out, new Packet(PacketType.PONG, p.getRequestId(), null));
          continue;
        }

        seen.add(p);
        return p;
      }
    }

    Packet request(PacketType type, Object data) throws Exception {
      var p = Packet.request(type, data);
      PacketIO.write(out, p);

      while (true) {
        var r = next();

        if (p.getRequestId().equals(r.getRequestId())) {
          return r;
        }
      }
    }

    Packet await(PacketType type) throws Exception {
      for (var p : seen) {
        if (p.getType() == type) {
          return p;
        }
      }

      while (true) {
        var p = next();

        if (p.getType() == type) {
          return p;
        }
      }
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }
}