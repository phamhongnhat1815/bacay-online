package com.game3cay.server.game;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.server.bus.*;
import com.game3cay.server.network.*;
import com.game3cay.server.room.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class AdminWatchTest {
  final IntegrationTest db = new IntegrationTest();
  @TempDir Path directory;
  AdminService admins;
  List<UserDTO> people;

  @BeforeEach
  void setup() throws Exception {
    db.directory = directory;
    db.database();

    try (var c = db.cp.open(); var s = c.createStatement()) {
      s.execute(
          "ALTER TABLE users ADD COLUMN role"
              + " VARCHAR(10) DEFAULT 'PLAYER' NOT NULL");

      s.execute(
          "CREATE TABLE admin_audit("
              + "request_id VARCHAR(64) PRIMARY KEY,"
              + "actor_id BIGINT,target_id BIGINT,"
              + "action VARCHAR(30),amount DECIMAL(15,2),"
              + "balance_before DECIMAL(15,2),"
              + "balance_after DECIMAL(15,2),"
              + "details VARCHAR(128),"
              + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }

    people = db.users(4);

    try (var c = db.cp.open();
        var q = c.prepareStatement(
            "UPDATE users SET role='ADMIN' WHERE id=?")) {
      q.setLong(1, people.get(0).userId());
      q.executeUpdate();
    }

    admins = new AdminService(db.cp);
  }

  @AfterEach
  void cleanup() throws Exception {
    db.cleanup();
  }

  record Delivery(Packet packet, List<Long> ids) {}

  static class Events implements RoomEvents {
    final List<Delivery> sent = new ArrayList<>();

    public void roomUpdated(RoomSnapshot r, List<Long> ids) {}
    public void chatMessage(long id, ChatMessage m, List<Long> ids) {}

    public void packet(Packet p, List<Long> ids) {
      sent.add(new Delivery(p, ids));
    }
  }

  long id(int i) {
    return people.get(i).userId();
  }

  void start(RoomManager rooms) throws Exception {
    rooms.join(people.get(1), new JoinRoomRequest(1));
    rooms.join(people.get(2), new JoinRoomRequest(1));
    rooms.setReady(id(1), true);
    rooms.setReady(id(2), true);
    rooms.start(id(1));
  }

  @Test
  void watchersReceiveLiveStateButNeverPrivateCardsOrRevealedHands()
      throws Exception {
    var events = new Events();

    try (var persistence = new ResultPersistence(db.games, directory)) {
      var rooms = new RoomManager(events, db.users, persistence);
      start(rooms);

      rooms.watch(people.get(3), new JoinRoomRequest(1), false);
      rooms.sendWatchState(id(3));
      rooms.watch(people.get(0), new JoinRoomRequest(1), true);
      rooms.sendWatchState(id(0));

      assertEquals(2, rooms.listRooms().getFirst().playerCount());

      assertEquals(
          ErrorCode.FORBIDDEN,
          assertThrows(
                  BUSException.class,
                  () -> rooms.setReady(id(3), true))
              .getErrorCode());

      String rid =
          events.sent.stream()
              .map(Delivery::packet).map(Packet::getData)
              .filter(GameStartInfo.class::isInstance)
              .map(GameStartInfo.class::cast)
              .findFirst().orElseThrow().roundId();

      assertEquals(
          ErrorCode.FORBIDDEN,
          assertThrows(
                  BUSException.class,
                  () -> rooms.action(
                      id(3),
                      new GameActionRequest(rid, BetAction.CALL, null)))
              .getErrorCode());

      rooms.action(
          id(2), new GameActionRequest(rid, BetAction.CALL, null));
      rooms.action(
          id(1), new GameActionRequest(rid, BetAction.CALL, null));

      var viewerPackets =
          events.sent.stream()
              .filter(x -> x.ids().contains(id(3)))
              .map(Delivery::packet).toList();

      assertTrue(
          viewerPackets.stream()
              .anyMatch(p -> p.getType() == PacketType.GAME_UPDATED));

      assertFalse(
          viewerPackets.stream().anyMatch(
              p -> Set.of(
                      PacketType.DEAL_CARD,
                      PacketType.PLAYER_STATE,
                      PacketType.ADMIN_TABLE_CARDS)
                  .contains(p.getType())));

      var result =
          viewerPackets.stream()
              .filter(p -> p.getType() == PacketType.ROUND_RESULT)
              .map(p -> (RoundResult) p.getData())
              .findFirst().orElseThrow();

      assertTrue(
          result.playerResults().stream()
              .allMatch(p -> p.hand() == null));

      var inspected =
          events.sent.stream()
              .filter(x -> x.ids().contains(id(0)))
              .map(Delivery::packet)
              .filter(p -> p.getType() == PacketType.ADMIN_TABLE_CARDS)
              .map(p -> (AdminPayload.TableCards) p.getData())
              .findFirst().orElseThrow();

      assertEquals(2, inspected.players().size());
      assertEquals(
          6,
          inspected.players().stream()
              .flatMap(p -> p.cards().stream()).distinct().count());

      rooms.leave(id(3));
      assertFalse(rooms.inRoom(id(3)));
    }
  }

  @Test
  void viewerCanWatchFullTableWithoutTakingASeat() throws Exception {
    var rooms = new RoomManager(new Events());

    for (long i = 1; i <= 8; i++) {
      rooms.join(
          new UserDTO(
              i, "p" + i, "P" + i, null, null,
              new BigDecimal("1000.00"), "ACTIVE", 0, 0, 0),
          new JoinRoomRequest(1));
    }

    rooms.watch(
        new UserDTO(
            99, "v", "V", null, null,
            BigDecimal.ZERO, "ACTIVE", 0, 0, 0),
        new JoinRoomRequest(1),
        false);

    assertEquals(8, rooms.listRooms().getFirst().playerCount());
    rooms.onDisconnected(99);
    assertFalse(rooms.inRoom(99));
  }

  @Test
  void creditRequiresAdminIsIdempotentAndRejectsOverflow()
      throws Exception {
    var request =
        new AdminPayload.Request(
            AdminPayload.Action.CREDIT,
            id(1), new BigDecimal("250.00"), null, 0);

    assertEquals(
        ErrorCode.FORBIDDEN,
        assertThrows(
                BUSException.class,
                () -> admins.change(
                    id(2), UUID.randomUUID().toString(), request))
            .getErrorCode());

    String key = UUID.randomUUID().toString();
    admins.change(id(0), key, request);
    admins.change(id(0), key, request);

    assertEquals(
        new BigDecimal("1250.00"),
        db.users.getProfile(id(1)).balance());
    assertEquals(1, db.count("admin_audit"));

    assertThrows(
        BUSException.class,
        () -> admins.change(
            id(0), key,
            new AdminPayload.Request(
                AdminPayload.Action.CREDIT,
                id(1), BigDecimal.ONE, null, 0)));

    assertThrows(
        BUSException.class,
        () -> admins.change(
            id(0), UUID.randomUUID().toString(),
            new AdminPayload.Request(
                AdminPayload.Action.CREDIT,
                id(1), new BigDecimal("9999999999999.99"), null, 0)));

    assertEquals(
        new BigDecimal("1250.00"),
        db.users.getProfile(id(1)).balance());
    assertEquals(1, db.count("admin_audit"));
  }

  @Test
  void activeRoundBlocksCreditAndDoesNotWriteAudit() throws Exception {
    try (var persistence = new ResultPersistence(db.games, directory)) {
      var rooms =
          new RoomManager(new Events(), db.users, persistence);
      start(rooms);

      assertEquals(
          ErrorCode.INVALID_STATE,
          assertThrows(
                  BUSException.class,
                  () -> rooms.creditWhenIdle(
                      id(1),
                      () -> admins.change(
                          id(0), UUID.randomUUID().toString(),
                          new AdminPayload.Request(
                              AdminPayload.Action.CREDIT,
                              id(1), BigDecimal.TEN, null, 0))))
              .getErrorCode());

      assertEquals(0, db.count("admin_audit"));
    }
  }

  @Test
  void blockUnblockAndPasswordResetPersistWithoutChangingRole()
      throws Exception {
    admins.change(
        id(0), UUID.randomUUID().toString(),
        new AdminPayload.Request(
            AdminPayload.Action.SET_BLOCKED,
            id(1), null, "BLOCKED", 0));

    assertThrows(
        BUSException.class,
        () -> db.users.authenticate(
            people.get(1).username(), "password123"));

    admins.change(
        id(0), UUID.randomUUID().toString(),
        new AdminPayload.Request(
            AdminPayload.Action.SET_BLOCKED,
            id(1), null, "ACTIVE", 0));

    admins.change(
        id(0), UUID.randomUUID().toString(),
        new AdminPayload.Request(
            AdminPayload.Action.RESET_PASSWORD,
            id(1), null, "changed123", 0));

    assertEquals(
        id(1),
        db.users.authenticate(
            people.get(1).username(), "changed123").userId());

    assertEquals("PLAYER", admins.roleOf(id(1)));

    assertThrows(
        BUSException.class,
        () -> admins.change(
            id(0), UUID.randomUUID().toString(),
            new AdminPayload.Request(
                AdminPayload.Action.SET_BLOCKED,
                id(0), null, "BLOCKED", 0)));
  }

  @Test
  @Timeout(20)
  void tcpRoleChecksAndPrivateInspectionSurviveActualFilter()
      throws Exception {
    var sessions = new SessionRegistry();

    try (var persistence = new ResultPersistence(db.games, directory);
        var server = new ServerSocket(0)) {
      var rooms =
          new RoomManager(
              new RoomEvents() {
                public void roomUpdated(
                    RoomSnapshot r, List<Long> ids) {
                  sessions.broadcast(
                      ids, Packet.serverPush(PacketType.ROOM_UPDATED, r));
                }

                public void chatMessage(
                    long id, ChatMessage m, List<Long> ids) {}

                public void packet(Packet p, List<Long> ids) {
                  sessions.broadcast(ids, p);
                }
              },
              db.users,
              persistence);

      var handlers = new ArrayList<ClientHandler>();
      var wires = new ArrayList<IntegrationTest.Wire>();
      var workers = Executors.newFixedThreadPool(4);

      try {
        for (int i = 0; i < 4; i++) {
          var s =
              new Socket("127.0.0.1", server.getLocalPort());

          var h =
              new ClientHandler(
                  server.accept(), db.users, sessions,
                  rooms, db.games, admins);

          handlers.add(h);
          workers.execute(h);

          var w = new IntegrationTest.Wire(s);
          wires.add(w);

          var auth =
              (AuthResult) w.request(
                      PacketType.LOGIN,
                      new AuthRequest(
                          people.get(i).username(), "password123"))
                  .getData();

          assertEquals(i == 0 ? "ADMIN" : "PLAYER", auth.role());
        }

        start(rooms);
        var admin = wires.get(0);
        var viewer = wires.get(3);

        viewer.request(
            PacketType.WATCH_ROOM, new JoinRoomRequest(1));

        assertEquals(
            ErrorCode.FORBIDDEN,
            ((Packet.ErrorPayload) viewer.request(
                    PacketType.ADMIN_REQUEST,
                    new AdminPayload.Request(
                        AdminPayload.Action.CREDIT,
                        id(1), BigDecimal.TEN, null, 0))
                .getData()).code());

        admin.request(
            PacketType.WATCH_ROOM, new JoinRoomRequest(1));

        var cards =
            (AdminPayload.TableCards)
                admin.await(PacketType.ADMIN_TABLE_CARDS).getData();

        assertEquals(2, cards.players().size());

        var response =
            admin.request(
                PacketType.ADMIN_REQUEST,
                new AdminPayload.Request(
                    AdminPayload.Action.LIST_USERS,
                    0, null, "", 0));

        assertEquals(
            4,
            ((AdminPayload.Response) response.getData())
                .accounts().size());

        viewer.request(PacketType.ROOM_LIST, null);

        assertFalse(
            viewer.seen.stream()
                .anyMatch(
                    p -> p.getType() == PacketType.ADMIN_TABLE_CARDS));
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
}