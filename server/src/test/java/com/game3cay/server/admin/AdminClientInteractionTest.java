package com.game3cay.server.admin;

import com.game3cay.server.bus.*;
import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.game.ResultPersistence;
import com.game3cay.server.network.ClientHandler;
import com.game3cay.server.network.SessionRegistry;
import com.game3cay.server.room.RoomEvents;
import com.game3cay.server.room.RoomManager;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import com.game3cay.shared.security.PasswordUtil;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real sockets and production event wiring; databases are isolated from installed game data. */
class AdminClientInteractionTest {
    @TempDir Path directory;
    private ConnectionProvider connections;
    private JdbcUserBUS users;
    private JdbcGameBUS games;
    private ResultPersistence persistence;
    private SessionRegistry sessions;
    private RoomManager rooms;
    private ServerSocket server;
    private ExecutorService workers;
    private String database;
    private final List<ClientHandler> handlers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean pauseAuthentication = new AtomicBoolean();
    private final CountDownLatch authenticationRead = new CountDownLatch(1);
    private final CountDownLatch resumeAuthentication = new CountDownLatch(1);

    @BeforeEach void startServer() throws Exception {
        String base = System.getenv("BACAY_TEST_MYSQL_URL");
        if (base == null) {
            String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
            connections = () -> DriverManager.getConnection(url);
        } else {
            database = "bacay_test_" + UUID.randomUUID().toString().replace("-", "");
            String user = System.getenv().getOrDefault("BACAY_TEST_MYSQL_USER", "root");
            String password = System.getenv().getOrDefault("BACAY_TEST_MYSQL_PASSWORD", "");
            try (var connection = DriverManager.getConnection(base, user, password);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
            }
            connections = () -> {
                var connection = DriverManager.getConnection(base, user, password);
                connection.setCatalog(database);
                return connection;
            };
        }
        try (var connection = connections.open(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(50) UNIQUE NOT NULL,"
                    + "password_hash VARCHAR(255) NOT NULL,display_name VARCHAR(100) NOT NULL,email VARCHAR(100),avatar_url VARCHAR(255),"
                    + "balance DECIMAL(15,2) NOT NULL DEFAULT 1000,status VARCHAR(20) DEFAULT 'ACTIVE',role VARCHAR(20) DEFAULT 'USER',"
                    + "total_games INT DEFAULT 0,total_wins INT DEFAULT 0,total_losses INT DEFAULT 0,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE play_rounds(id BIGINT AUTO_INCREMENT PRIMARY KEY,round_id VARCHAR(64) UNIQUE,content_hash VARCHAR(64),"
                    + "runtime_room_id BIGINT,room_name VARCHAR(100),ante DECIMAL(15,2),status VARCHAR(20),ended_at BIGINT)");
            statement.execute("CREATE TABLE play_results(round_id VARCHAR(64),user_id BIGINT,display_name VARCHAR(100),placement INT,"
                    + "outcome VARCHAR(20),profit DECIMAL(15,2),hand_type VARCHAR(20),score INT,PRIMARY KEY(round_id,user_id))");
            statement.execute("CREATE TABLE play_cards(round_id VARCHAR(64),user_id BIGINT,position_no INT,rank_name VARCHAR(10),"
                    + "suit_name VARCHAR(10),PRIMARY KEY(round_id,user_id,position_no))");
            statement.execute("CREATE TABLE play_bets(round_id VARCHAR(64),sequence_no INT,user_id BIGINT,action VARCHAR(20),"
                    + "total_bet DECIMAL(15,2),added_amount DECIMAL(15,2),created_at BIGINT,PRIMARY KEY(round_id,sequence_no))");
            statement.execute("CREATE TABLE play_pots(round_id VARCHAR(64),pot_no INT,amount DECIMAL(15,2),PRIMARY KEY(round_id,pot_no))");
            statement.execute("CREATE TABLE play_pot_players(round_id VARCHAR(64),pot_no INT,user_id BIGINT,PRIMARY KEY(round_id,pot_no,user_id))");
            statement.execute("CREATE TABLE play_ledger(round_id VARCHAR(64),user_id BIGINT,delta DECIMAL(15,2),balance_before DECIMAL(15,2),"
                    + "balance_after DECIMAL(15,2),PRIMARY KEY(round_id,user_id))");
            statement.execute("CREATE TABLE admin_adjustments(id BIGINT AUTO_INCREMENT PRIMARY KEY,adjustment_id VARCHAR(64) UNIQUE,"
                    + "admin_user_id BIGINT,target_user_id BIGINT,delta DECIMAL(15,2),balance_before DECIMAL(15,2),"
                    + "balance_after DECIMAL(15,2),reason VARCHAR(255),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            String hash = PasswordUtil.hash("password123");
            try (var insert = connection.prepareStatement("INSERT INTO users(username,password_hash,display_name,role) VALUES(?,?,?,?)")) {
                for (String name : List.of("player_one", "player_two", "admin_boss", "admin_backup")) {
                    insert.setString(1, name); insert.setString(2, hash); insert.setString(3, name);
                    insert.setString(4, name.startsWith("admin") ? "ADMIN" : "USER"); insert.executeUpdate();
                }
            }
        }
        users = new JdbcUserBUS(connections);
        games = new JdbcGameBUS(connections);
        UserBUS networkUsers = (UserBUS) Proxy.newProxyInstance(UserBUS.class.getClassLoader(), new Class<?>[]{UserBUS.class},
                (proxy, method, arguments) -> {
                    Object result;
                    try { result = method.invoke(users, arguments); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                    if (method.getName().equals("authenticate") && "player_one".equals(arguments[0])
                            && pauseAuthentication.compareAndSet(true, false)) {
                        authenticationRead.countDown();
                        if (!resumeAuthentication.await(5, TimeUnit.SECONDS)) throw new AssertionError("Authentication latch timed out");
                    }
                    return result;
                });
        sessions = new SessionRegistry();
        persistence = new ResultPersistence(games, directory);
        rooms = new RoomManager(new RoomEvents() {
            @Override public void roomUpdated(RoomSnapshot room, List<Long> recipients) {
                sessions.broadcast(recipients, Packet.serverPush(PacketType.ROOM_UPDATED, room));
            }
            @Override public void chatMessage(long roomId, ChatMessage message, List<Long> recipients) {
                sessions.broadcast(recipients, Packet.serverPush("SYSTEM".equals(message.type())
                        ? PacketType.CHAT_SYSTEM : PacketType.CHAT_MESSAGE, new RoomChatEvent(roomId, message)));
            }
            @Override public void packet(Packet packet, List<Long> recipients) { sessions.broadcast(recipients, packet); }
            @Override public void lobbyUpdated(List<RoomSummary> tables) { sessions.broadcastAll(Packet.serverPush(PacketType.ROOM_LIST_RESULT, tables)); }
        }, users, persistence, () -> 0L);
        server = new ServerSocket(0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        workers.submit(() -> {
            try {
                while (!server.isClosed()) {
                    var handler = new ClientHandler(server.accept(), networkUsers, sessions, rooms, games);
                    handlers.add(handler); workers.submit(handler);
                }
            } catch (IOException error) { if (!server.isClosed()) throw new RuntimeException(error); }
        });
    }

    @AfterEach void stopServer() throws Exception {
        resumeAuthentication.countDown();
        if (server != null) server.close();
        handlers.forEach(ClientHandler::close);
        if (workers != null) { workers.shutdown(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)); }
        if (rooms != null) rooms.close();
        if (persistence != null) persistence.close();
        if (database != null) {
            assertTrue(database.matches("bacay_test_[a-f0-9]{32}"));
            try (var connection = connections.open(); var statement = connection.createStatement()) { statement.execute("DROP DATABASE " + database); }
        }
    }

    @Test void adjustmentNotifiesOnlyItsOwnerInLobbyAndWaitingRoom() throws Exception {
        try (var admin = login("admin_boss"); var player = login("player_one"); var peer = login("player_two")) {
            assertAck(admin.request(PacketType.ADMIN_ADJUST_BALANCE_REQ, adjustment(1, "100")));
            assertBalance(player.await(p -> privateProfile(p, 1, "1100.00")), "1100.00");
            player.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
            peer.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
            RoomSnapshot ready = (RoomSnapshot) player.request(PacketType.SET_READY, true).getData();
            assertTrue(ready.players().stream().filter(p -> p.userId() == 1).findFirst().orElseThrow().ready());
            assertAck(admin.request(PacketType.ADMIN_ADJUST_BALANCE_REQ, adjustment(1, "25")));
            assertBalance(player.await(p -> privateProfile(p, 1, "1125.00")), "1125.00");
            var updated = (RoomSnapshot) player.await(p -> p.getType() == PacketType.ROOM_UPDATED
                    && ((RoomSnapshot) p.getData()).stateVersion() > ready.stateVersion()).getData();
            assertFalse(updated.players().stream().filter(p -> p.userId() == 1).findFirst().orElseThrow().ready());
            peer.request(PacketType.GET_PROFILE, null);
            assertFalse(peer.seen.stream().anyMatch(p -> p.getRequestId() == null && p.getType() == PacketType.PROFILE_RESULT));
            assertEquals(new BigDecimal("1125.00"), users.getProfile(1).balance());
        }
    }

    @Test void resetCancelsAndSettlesLiveRoundWithoutDuplicatingMoney() throws Exception {
        try (var admin = login("admin_boss"); var player = login("player_one"); var peer = login("player_two")) {
            startRound(player, peer);
            var privateState = (PlayerStateSnapshot) player.await(p -> p.getType() == PacketType.PLAYER_STATE).getData();
            var detail = (AdminLiveMatchDetail) admin.request(PacketType.ADMIN_INSPECT_ROOM_REQ, 1L).getData();
            assertEquals(privateState.myBalance(), detail.players().stream().filter(p -> p.userId() == 1).findFirst().orElseThrow().balance());
            RoomSnapshot playing = (RoomSnapshot) player.await(p -> p.getType() == PacketType.ROOM_UPDATED
                    && "PLAYING".equals(((RoomSnapshot) p.getData()).status())).getData();
            assertError(admin.request(PacketType.ADMIN_ADJUST_BALANCE_REQ, adjustment(1, "100")), ErrorCode.INVALID_STATE);
            assertAck(admin.request(PacketType.ADMIN_FORCE_RESET_ROOM, new AdminResetRoomRequest(1, "interaction test")));
            RoundResult result = (RoundResult) player.await(p -> p.getType() == PacketType.ROUND_RESULT).getData();
            assertEquals("CANCELLED", result.roundStatus());
            assertEquals(0, result.playerResults().stream().map(PlayerResult::profit).reduce(BigDecimal.ZERO, BigDecimal::add).signum());
            awaitSettled(1);
            assertEquals(new BigDecimal("2000.00"), users.getProfile(1).balance().add(users.getProfile(2).balance()));
            var reset = (RoomSnapshot) player.await(p -> p.getType() == PacketType.ROOM_UPDATED
                    && "WAITING".equals(((RoomSnapshot) p.getData()).status())
                    && ((RoomSnapshot) p.getData()).stateVersion() > playing.stateVersion()).getData();
            assertTrue(reset.players().stream().noneMatch(RoomSnapshot.PlayerSlot::ready));
            assertAck(admin.request(PacketType.ADMIN_FORCE_RESET_ROOM, new AdminResetRoomRequest(1, "repeat reset")));
            assertEquals(0, users.getLedgerForAdmin(0, 20).totalItems());
            assertEquals(0, users.getProfile(1).totalGames());
            assertEquals(1, games.getHistory(1, 0, 20).totalItems());
            assertAck(admin.request(PacketType.ADMIN_ADJUST_BALANCE_REQ, adjustment(1, "5")));
            assertBalance(player.await(p -> privateProfile(p, 1, "1005.00")), "1005.00");
        }
    }

    @Test void blockedOnlinePlayerReceivesReasonAndCannotReconnectUntilUnlocked() throws Exception {
        try (var admin = login("admin_boss"); var player = login("player_one")) {
            player.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
            assertAck(admin.request(PacketType.ADMIN_LOCK_USER_REQ, new AdminLockUserRequest(1, true)));
            Packet notice = player.await(p -> p.getType() == PacketType.ERROR);
            assertNull(notice.getRequestId());
            assertError(notice, ErrorCode.USER_BLOCKED);
            try (var retry = wire()) { assertError(retry.request(PacketType.LOGIN, new AuthRequest("player_one", "password123")), ErrorCode.USER_BLOCKED); }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (sessions.find(1).isPresent() && System.nanoTime() < deadline) Thread.sleep(5);
            assertTrue(sessions.find(1).isEmpty());
            assertFalse(rooms.inRoom(1));
            assertAck(admin.request(PacketType.ADMIN_LOCK_USER_REQ, new AdminLockUserRequest(1, false)));
            try (var unblocked = login("player_one")) { assertEquals(PacketType.ROOM_LIST_RESULT, unblocked.request(PacketType.ROOM_LIST, null).getType()); }
        }
    }

    @Test void lockDuringAuthenticationCannotCreateAnActiveBlockedSession() throws Exception {
        try (var admin = login("admin_boss"); var player = wire()) {
            pauseAuthentication.set(true);
            var authentication = workers.submit(() -> player.request(PacketType.LOGIN, new AuthRequest("player_one", "password123")));
            assertTrue(authenticationRead.await(3, TimeUnit.SECONDS));
            assertAck(admin.request(PacketType.ADMIN_LOCK_USER_REQ, new AdminLockUserRequest(1, true)));
            resumeAuthentication.countDown();
            assertError(authentication.get(3, TimeUnit.SECONDS), ErrorCode.USER_BLOCKED);
            assertTrue(sessions.find(1).isEmpty());
        }
    }

    @Test void adminCannotLockItsOwnOrAnotherAdminAccount() throws Exception {
        try (var admin = login("admin_boss")) {
            assertError(admin.request(PacketType.ADMIN_LOCK_USER_REQ, new AdminLockUserRequest(3, true)), ErrorCode.FORBIDDEN);
            assertError(admin.request(PacketType.ADMIN_LOCK_USER_REQ, new AdminLockUserRequest(4, true)), ErrorCode.FORBIDDEN);
            assertEquals("ACTIVE", users.getProfile(3).status());
            assertEquals("ACTIVE", users.getProfile(4).status());
            assertEquals(PacketType.ADMIN_STATS_RESP, admin.request(PacketType.ADMIN_STATS_REQ, null).getType());
        }
    }

    @Test void lockingDuringPlaySettlesTheFoldAndKeepsTheOtherClientConnected() throws Exception {
        try (var admin = login("admin_boss"); var player = login("player_one"); var peer = login("player_two")) {
            startRound(player, peer);
            assertAck(admin.request(PacketType.ADMIN_LOCK_USER_REQ, new AdminLockUserRequest(1, true)));
            assertError(player.await(p -> p.getType() == PacketType.ERROR), ErrorCode.USER_BLOCKED);
            RoundResult result = (RoundResult) peer.await(p -> p.getType() == PacketType.ROUND_RESULT).getData();
            assertEquals("FINISHED", result.roundStatus());
            awaitSettled(2);
            assertEquals(new BigDecimal("2000.00"), users.getProfile(1).balance().add(users.getProfile(2).balance()));
            assertEquals(2, users.getLedgerForAdmin(0, 20).totalItems());
            assertEquals(PacketType.PROFILE_RESULT, peer.request(PacketType.GET_PROFILE, null).getType());
            assertEquals(PacketType.ADMIN_STATS_RESP, admin.request(PacketType.ADMIN_STATS_REQ, null).getType());
        }
    }

    @Test void repeatedAdjustmentRequestIdDoesNotApplyTwiceAndRejectedDebitLeavesNoLedgerEntry() throws Exception {
        try (var admin = login("admin_boss"); var player = login("player_one")) {
            Packet adjustment = Packet.request(PacketType.ADMIN_ADJUST_BALANCE_REQ, adjustment(1, "50"));
            assertAck(admin.request(adjustment));
            assertAck(admin.request(adjustment));
            assertEquals(new BigDecimal("1050.00"), users.getProfile(1).balance());
            assertEquals(1, users.getLedgerForAdmin(0, 20).totalItems());
            assertError(admin.request(PacketType.ADMIN_ADJUST_BALANCE_REQ, adjustment(1, "-2000")), ErrorCode.INVALID_BET_AMOUNT);
            assertEquals(new BigDecimal("1050.00"), users.getProfile(1).balance());
            assertEquals(1, users.getLedgerForAdmin(0, 20).totalItems());
            assertEquals(PacketType.PROFILE_RESULT, player.request(PacketType.GET_PROFILE, null).getType());
        }
    }

    @Test void maximumBroadcastReachesLobbyAndTableWithoutLeakingPrivateGamePackets() throws Exception {
        try (var admin = login("admin_boss"); var player = login("player_one"); var peer = login("player_two")) {
            player.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
            for (String level : List.of("INFO", "WARNING", "URGENT")) {
                String message = level + "x".repeat(500 - level.length());
                assertAck(admin.request(PacketType.ADMIN_BROADCAST_REQ, new AdminBroadcastRequest(message, level)));
                for (Wire recipient : List.of(player, peer)) {
                    var event = (RoomChatEvent) recipient.await(p -> p.getType() == PacketType.CHAT_SYSTEM
                            && ((RoomChatEvent) p.getData()).message().content().endsWith(message)).getData();
                    assertEquals(0, event.roomId());
                    assertEquals("SYSTEM", event.message().type());
                }
            }
            peer.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
            player.request(PacketType.SET_READY, true); peer.request(PacketType.SET_READY, true);
            assertAck(player.request(PacketType.START_GAME_REQUEST, null));
            player.await(p -> p.getType() == PacketType.PLAYER_STATE);
            peer.await(p -> p.getType() == PacketType.PLAYER_STATE);
            admin.request(PacketType.ADMIN_ROOMS_MONITOR_REQ, null);
            assertFalse(admin.seen.stream().anyMatch(p -> p.getType() == PacketType.PLAYER_STATE || p.getType() == PacketType.DEAL_CARD));
            for (Wire recipient : List.of(player, peer)) {
                assertTrue(recipient.seen.stream().filter(p -> p.getType() == PacketType.GAME_UPDATED)
                        .allMatch(p -> p.getData() instanceof GameStateSnapshot));
            }
        }
    }

    private void startRound(Wire player, Wire peer) throws Exception {
        player.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1)); peer.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
        player.request(PacketType.SET_READY, true); peer.request(PacketType.SET_READY, true);
        assertAck(player.request(PacketType.START_GAME_REQUEST, null));
    }
    private void awaitSettled(long userId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (persistence.pendingFor(userId) && System.nanoTime() < deadline) Thread.sleep(5);
        assertFalse(persistence.pendingFor(userId));
    }
    private static AdminAdjustBalanceRequest adjustment(long userId, String amount) {
        return new AdminAdjustBalanceRequest(userId, new BigDecimal(amount), "Interaction regression");
    }
    private static boolean privateProfile(Packet packet, long userId, String balance) {
        return packet.getRequestId() == null && packet.getData() instanceof ProfileResult profile
                && profile.userId() == userId && profile.balance().compareTo(new BigDecimal(balance)) == 0;
    }
    private static void assertBalance(Packet packet, String amount) { assertEquals(new BigDecimal(amount), ((ProfileResult) packet.getData()).balance()); }
    private static void assertAck(Packet packet) { assertEquals(PacketType.ACK, packet.getType(), packet.toString()); }
    private static void assertError(Packet packet, ErrorCode error) {
        assertEquals(PacketType.ERROR, packet.getType(), packet.toString());
        assertEquals(error, ((Packet.ErrorPayload) packet.getData()).code());
    }
    private Wire wire() throws Exception { return new Wire(new Socket("127.0.0.1", server.getLocalPort())); }
    private Wire login(String username) throws Exception {
        Wire wire = wire();
        Packet result = wire.request(PacketType.LOGIN, new AuthRequest(username, "password123"));
        assertEquals(PacketType.AUTH_RESULT, result.getType());
        assertTrue(((AuthResult) result.getData()).success()); return wire;
    }
    private static final class Wire implements AutoCloseable {
        private final Socket socket;
        private final PacketIO.Input input;
        private final PacketIO.Output output;
        private final List<Packet> seen = new ArrayList<>();
        Wire(Socket socket) throws Exception {
            this.socket = socket; output = PacketIO.openOutput(socket); input = PacketIO.openInput(socket);
            input.setIdleTimeoutMillis(3000);
        }
        Packet request(PacketType type, Object payload) throws Exception { return request(Packet.request(type, payload)); }
        Packet request(Packet request) throws Exception {
            PacketIO.write(output, request);
            while (true) { Packet reply = next(); if (request.getRequestId().equals(reply.getRequestId())) return reply; }
        }
        Packet await(Predicate<Packet> matches) throws Exception {
            for (Packet packet : seen) if (matches.test(packet)) return packet;
            while (true) { Packet packet = next(); if (matches.test(packet)) return packet; }
        }
        private Packet next() throws Exception {
            while (true) {
                Packet packet = PacketIO.read(input);
                if (packet.getType() == PacketType.PING) { PacketIO.write(output, new Packet(PacketType.PONG, packet.getRequestId(), null)); }
                else { seen.add(packet); return packet; }
            }
        }
        @Override public void close() throws IOException { socket.close(); }
    }
}
