package com.game3cay.server.bus;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.shared.network.ErrorCode;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class JdbcUserBUSTest {
  private ConnectionProvider connections;
  private JdbcUserBUS bus;

  @BeforeEach
  void database() throws Exception {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    connections = () -> DriverManager.getConnection(url);
    try (Connection c = connections.open();
        Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) UNIQUE"
              + " NOT NULL, password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(100) NOT"
              + " NULL, email VARCHAR(100) UNIQUE, avatar_url VARCHAR(255), balance DECIMAL(15,2)"
              + " DEFAULT 0 NOT NULL, status VARCHAR(20) DEFAULT 'ACTIVE', total_games INT DEFAULT"
              + " 0, total_wins INT DEFAULT 0, total_losses INT DEFAULT 0)");
    }
    bus = new JdbcUserBUS(connections);
  }

  @Test
  void registerLoginAndHash() throws Exception {
    UserDTO user = bus.register("Nhat_01", "password123", "Nhật", null);
    assertEquals("nhat_01", user.username());
    assertEquals(user.userId(), bus.authenticate("NHAT_01", "password123").userId());
    assertEquals("Nhật", bus.getProfile(user.userId()).displayName());
    try (Connection c = connections.open();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT password_hash FROM users")) {
      assertTrue(r.next());
      assertNotEquals("password123", r.getString(1));
      assertTrue(r.getString(1).startsWith("PBKDF2WithHmacSHA256$"));
    }
    assertCode(ErrorCode.INVALID_CREDENTIALS, () -> bus.authenticate("nhat_01", "wrong"));
    assertCode(ErrorCode.INVALID_CREDENTIALS, () -> bus.authenticate("missing", "password123"));
    assertCode(ErrorCode.USERNAME_EXISTS, () -> bus.register("NHAT_01", "password123", null, null));
  }

  @Test
  void blockedAndValidationAndPasswordChange() throws Exception {
    UserDTO user = bus.register("blocked", "password123", null, null);
    try (Connection c = connections.open();
        Statement s = c.createStatement()) {
      s.executeUpdate("UPDATE users SET status='BLOCKED'");
    }
    assertCode(ErrorCode.USER_BLOCKED, () -> bus.authenticate("blocked", "password123"));
    assertCode(ErrorCode.INVALID_CREDENTIALS, () -> bus.authenticate("blocked", "wrong"));
    assertCode(
        ErrorCode.INVALID_REQUEST, () -> bus.register("a' OR 1=1", "password123", null, null));
    assertCode(ErrorCode.INVALID_REQUEST, () -> bus.register("valid", "short", null, null));
    assertCode(
        ErrorCode.FORBIDDEN, () -> bus.changePassword(user.userId(), "wrong", "nextpassword"));
    bus.changePassword(user.userId(), "password123", "nextpassword");
    assertCode(ErrorCode.INVALID_CREDENTIALS, () -> bus.authenticate("blocked", "password123"));
    assertCode(ErrorCode.USER_BLOCKED, () -> bus.authenticate("blocked", "nextpassword"));
  }

  @Test
  void simultaneousRegistrationHasOneWinner() throws Exception {
    ExecutorService workers = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<ErrorCode> register =
        () -> {
          start.await();
          try {
            bus.register("same_name", "password123", null, null);
            return null;
          } catch (BUSException e) {
            return e.getErrorCode();
          }
        };
    try {
      Future<ErrorCode> a = workers.submit(register);
      Future<ErrorCode> b = workers.submit(register);
      start.countDown();
      ErrorCode first = a.get(10, TimeUnit.SECONDS);
      ErrorCode second = b.get(10, TimeUnit.SECONDS);
      assertTrue(
          (first == null && second == ErrorCode.USERNAME_EXISTS)
              || (second == null && first == ErrorCode.USERNAME_EXISTS));
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void duplicateEmailDoesNotReportDuplicateUsername() throws Exception {
    bus.register("first_email", "password123", null, "nhat@example.com");
    assertCode(
        ErrorCode.INVALID_REQUEST,
        () -> bus.register("other_name", "password123", null, "nhat@example.com"));
    assertCode(
        ErrorCode.USERNAME_EXISTS,
        () -> bus.register("first_email", "password123", null, "new@example.com"));
  }

  @Test
  void jdbcUsersWorkWithTwoIndependentRooms() throws Exception {
    record Delivery(
        long roomId,
        com.game3cay.shared.model.ChatMessage message,
        java.util.List<Long> recipients) {}
    java.util.List<Delivery> chat = new java.util.ArrayList<>();
    com.game3cay.server.room.RoomManager rooms =
        new com.game3cay.server.room.RoomManager(
            new com.game3cay.server.room.RoomEvents() {
              public void roomUpdated(
                  com.game3cay.shared.model.RoomSnapshot room, java.util.List<Long> recipients) {}

              public void chatMessage(
                  long roomId,
                  com.game3cay.shared.model.ChatMessage message,
                  java.util.List<Long> recipients) {
                chat.add(new Delivery(roomId, message, java.util.List.copyOf(recipients)));
              }
            });
    UserDTO[] players = new UserDTO[4];
    for (int i = 0; i < players.length; i++) {
      bus.register("player_" + i, "password123", "Người " + i, null);
      players[i] = bus.authenticate("player_" + i, "password123");
    }
    var settings =
        new com.game3cay.shared.model.CreateRoomRequest(
            "Phòng Nhật", 2, 2, java.math.BigDecimal.ONE);
    var first = rooms.join(players[0], new com.game3cay.shared.model.JoinRoomRequest(1));
    rooms.join(players[1], new com.game3cay.shared.model.JoinRoomRequest(first.roomId()));
    var second = rooms.join(players[2], new com.game3cay.shared.model.JoinRoomRequest(2));
    rooms.join(players[3], new com.game3cay.shared.model.JoinRoomRequest(second.roomId()));
    assertCode(
        ErrorCode.ALREADY_IN_ROOM,
        () ->
            rooms.join(players[0], new com.game3cay.shared.model.JoinRoomRequest(second.roomId())));
    assertTrue(
        rooms.setReady(players[1].userId(), true).players().stream()
            .anyMatch(p -> p.userId() == players[1].userId() && p.ready()));
    chat.clear();
    rooms.chat(players[0].userId(), "Xin chào phòng một");
    Delivery event = chat.getLast();
    assertEquals(first.roomId(), event.roomId());
    assertEquals(players[0].userId(), event.message().senderId());
    assertEquals("Người 0", event.message().senderDisplayName());
    assertEquals(
        java.util.Set.of(players[0].userId(), players[1].userId()),
        new java.util.HashSet<>(event.recipients()));
    rooms.onDisconnected(players[0].userId());
    var remaining = rooms.setReady(players[1].userId(), false);
    assertEquals(players[1].userId(), remaining.ownerUserId());
    rooms.leave(players[1].userId());
    assertEquals(20, rooms.listRooms().size());
    assertEquals(0, rooms.listRooms().getFirst().playerCount());
    assertEquals(2, rooms.listRooms().get(1).playerCount());

    assertEquals(players[0].userId(), bus.authenticate("player_0", "password123").userId());
  }

  @Test
  @Timeout(15)
  void tcpAuthenticationRoomChatAndLogout() throws Exception {
    var sessions = new com.game3cay.server.network.SessionRegistry();
    var rooms =
        new com.game3cay.server.room.RoomManager(
            new com.game3cay.server.room.RoomEvents() {
              public void roomUpdated(
                  com.game3cay.shared.model.RoomSnapshot room, java.util.List<Long> recipients) {
                sessions.broadcast(
                    recipients,
                    new com.game3cay.shared.network.Packet(
                        com.game3cay.shared.network.PacketType.ROOM_UPDATED, null, room));
              }

              public void chatMessage(
                  long id,
                  com.game3cay.shared.model.ChatMessage message,
                  java.util.List<Long> recipients) {
                sessions.broadcast(
                    recipients,
                    new com.game3cay.shared.network.Packet(
                        "SYSTEM".equals(message.type())
                            ? com.game3cay.shared.network.PacketType.CHAT_SYSTEM
                            : com.game3cay.shared.network.PacketType.CHAT_MESSAGE,
                        null,
                        new com.game3cay.shared.model.RoomChatEvent(id, message)));
              }
            });
    ExecutorService workers = Executors.newFixedThreadPool(2);
    java.util.List<com.game3cay.server.network.ClientHandler> handlers =
        new java.util.ArrayList<>();
    try (var listener = new java.net.ServerSocket(0);
        var first = new java.net.Socket("127.0.0.1", listener.getLocalPort());
        var second = new java.net.Socket("127.0.0.1", listener.getLocalPort())) {
      for (int i = 0; i < 2; i++) {
        var handler =
            new com.game3cay.server.network.ClientHandler(listener.accept(), bus, sessions, rooms);
        handlers.add(handler);
        workers.submit(handler);
      }
      try (var a = new Wire(first);
          var b = new Wire(second)) {
        var denied = a.request(com.game3cay.shared.network.PacketType.ROOM_LIST, null);
        assertEquals(
            ErrorCode.UNAUTHENTICATED,
            ((com.game3cay.shared.network.Packet.ErrorPayload) denied.getData()).code());
        a.request(
            com.game3cay.shared.network.PacketType.REGISTER,
            new com.game3cay.shared.model.RegisterRequest(
                "tcp_user", "password123", "Nhật TCP", null));
        assertEquals(
            com.game3cay.shared.network.PacketType.ERROR,
            a.request(com.game3cay.shared.network.PacketType.ROOM_LIST, null).getType());
        var auth =
            (com.game3cay.shared.model.AuthResult)
                a.request(
                        com.game3cay.shared.network.PacketType.LOGIN,
                        new com.game3cay.shared.model.AuthRequest("tcp_user", "password123"))
                    .getData();
        var duplicate =
            b.request(
                com.game3cay.shared.network.PacketType.LOGIN,
                new com.game3cay.shared.model.AuthRequest("tcp_user", "password123"));
        assertEquals(
            ErrorCode.ALREADY_LOGGED_IN,
            ((com.game3cay.shared.network.Packet.ErrorPayload) duplicate.getData()).code());
        var room =
            (com.game3cay.shared.model.RoomSnapshot)
                a.request(
                        com.game3cay.shared.network.PacketType.JOIN_ROOM,
                        new com.game3cay.shared.model.JoinRoomRequest(1))
                    .getData();
        assertEquals(auth.userId().longValue(), room.ownerUserId());
        var ack =
            a.request(
                com.game3cay.shared.network.PacketType.CHAT_MESSAGE,
                new com.game3cay.shared.model.ChatMessage(999L, "Giả tên", "Xin chào", "TEXT", 1));
        assertEquals(com.game3cay.shared.network.PacketType.ACK, ack.getType());
        var chat =
            a.events.stream()
                .filter(p -> p.getType() == com.game3cay.shared.network.PacketType.CHAT_MESSAGE)
                .map(p -> (com.game3cay.shared.model.RoomChatEvent) p.getData())
                .findFirst()
                .orElseThrow();
        assertEquals(auth.userId(), chat.message().senderId());
        assertEquals("Nhật TCP", chat.message().senderDisplayName());
        assertEquals(room.roomId(), chat.roomId());
        a.request(com.game3cay.shared.network.PacketType.LOGOUT, null);
        assertEquals(20, rooms.listRooms().size());
        assertTrue(rooms.listRooms().stream().allMatch(t -> t.playerCount() == 0));
        assertEquals(
            com.game3cay.shared.network.PacketType.AUTH_RESULT,
            b.request(
                    com.game3cay.shared.network.PacketType.LOGIN,
                    new com.game3cay.shared.model.AuthRequest("tcp_user", "password123"))
                .getType());
      }
    } finally {
      handlers.forEach(com.game3cay.server.network.ClientHandler::close);
      workers.shutdownNow();
      assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
    }
  }

  private static final class Wire implements AutoCloseable {
    private final java.io.ObjectOutputStream out;
    private final java.io.ObjectInputStream in;
    private final java.util.List<com.game3cay.shared.network.Packet> events =
        new java.util.ArrayList<>();

    Wire(java.net.Socket socket) throws Exception {
      socket.setSoTimeout(3000);
      out = new java.io.ObjectOutputStream(socket.getOutputStream());
      out.flush();
      in = com.game3cay.shared.network.PacketIO.openInput(socket);
    }

    com.game3cay.shared.network.Packet request(
        com.game3cay.shared.network.PacketType type, Object data) throws Exception {
      var request = com.game3cay.shared.network.Packet.request(type, data);
      com.game3cay.shared.network.PacketIO.write(out, request);
      while (true) {
        var packet = com.game3cay.shared.network.PacketIO.read(in);
        if (packet.getType() == com.game3cay.shared.network.PacketType.PING) {
          com.game3cay.shared.network.PacketIO.write(
              out,
              new com.game3cay.shared.network.Packet(
                  com.game3cay.shared.network.PacketType.PONG, packet.getRequestId(), null));
        } else if (request.getRequestId().equals(packet.getRequestId())) return packet;
        else events.add(packet);
      }
    }

    public void close() throws Exception {
      in.close();
      out.close();
    }
  }

  @Test
  void unavailableDatabaseReturnsControlledError() {
    JdbcUserBUS unavailable =
        new JdbcUserBUS(
            () -> {
              throw new SQLException("DB down");
            });
    assertCode(ErrorCode.INTERNAL_ERROR, () -> unavailable.authenticate("valid", "password123"));
    assertCode(
        ErrorCode.INTERNAL_ERROR, () -> unavailable.register("valid", "password123", null, null));
  }

  private static void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
    assertEquals(code, assertThrows(BUSException.class, action).getErrorCode());
  }
}
