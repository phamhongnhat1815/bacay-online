package com.game3cay.server.network;

import com.game3cay.server.bus.*;
import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.room.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.net.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PointTransferWireTest {
    ConnectionProvider connections;
    JdbcUserBUS users;
    JdbcPointTransferBUS points;
    SessionRegistry sessions;
    RoomManager rooms;
    ServerSocket server;
    ExecutorService workers;
    List<ClientHandler> handlers = new CopyOnWriteArrayList<>();
    List<Wire> wires = new ArrayList<>();
    AtomicLong clock = new AtomicLong(System.currentTimeMillis());
    UserDTO alice, bob, carol;

    @BeforeEach void setup() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        connections = () -> DriverManager.getConnection(url);
        try (var connection = connections.open(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(50) UNIQUE NOT NULL,"
                    + "password_hash VARCHAR(255) NOT NULL,display_name VARCHAR(100) NOT NULL,email VARCHAR(100) UNIQUE,"
                    + "avatar_url VARCHAR(255),balance DECIMAL(15,2) DEFAULT 1000 NOT NULL,status VARCHAR(20) DEFAULT 'ACTIVE',"
                    + "role VARCHAR(20) DEFAULT 'USER',total_games INT DEFAULT 3,total_wins INT DEFAULT 0,total_losses INT DEFAULT 0,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            PointTestSchema.create(connection);
        }
        users = new JdbcUserBUS(connections);
        alice = users.register("alice", "password123", "Alice", null);
        bob = users.register("bob", "password123", "Bob", null);
        carol = users.register("carol", "password123", "Carol", null);
        points = new JdbcPointTransferBUS(connections,
                new PointTransferPolicy(3, new BigDecimal("2000"), new BigDecimal("5000"), 2, 3), clock::get);
        sessions = new SessionRegistry();
        rooms = new RoomManager(new RoomEvents() {
            public void roomUpdated(RoomSnapshot room, List<Long> recipients) { sessions.broadcast(recipients, Packet.serverPush(PacketType.ROOM_UPDATED, room)); }
            public void chatMessage(long room, ChatMessage message, List<Long> recipients) {}
            public void packet(Packet packet, List<Long> recipients) { sessions.broadcast(recipients, packet); }
            public void lobbyUpdated(List<RoomSummary> tables) {}
        }, users, null);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server = new ServerSocket(0);
        workers.submit(() -> {
            try {
                while (!server.isClosed()) {
                    ClientHandler handler = new ClientHandler(server.accept(), users, sessions, rooms, null, points);
                    handlers.add(handler); workers.submit(handler);
                }
            } catch (java.io.IOException expectedWhenClosed) {}
        });
    }

    @AfterEach void cleanup() throws Exception {
        for (Wire wire : wires) wire.close();
        server.close(); handlers.forEach(ClientHandler::close); workers.shutdownNow(); workers.awaitTermination(3, TimeUnit.SECONDS); rooms.close();
    }

    @Test void directGiftUpdatesOnlyParticipantsAndDurablyReplaysAfterReconnectOutsideRoom() throws Exception {
        Wire a = login("alice"), b = login("bob"), c = login("carol");
        a.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1)); b.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
        var gift = new PointTransferRequest(id(), bob.userId(), new BigDecimal("100.00"), "Chơi cùng nhau");
        Packet result = a.request(PacketType.POINT_TRANSFER, gift);
        assertEquals(PacketType.POINT_TRANSFER_RESULT, result.getType());
        var receipt = (PointTransferReceipt) result.getData();
        assertEquals(new BigDecimal("900.00"), users.getProfile(alice.userId()).balance());
        assertEquals(new BigDecimal("1100.00"), users.getProfile(bob.userId()).balance());
        b.request(PacketType.GET_PROFILE, null); c.request(PacketType.GET_PROFILE, null);
        assertTrue(b.pushes.stream().anyMatch(packet -> packet.getType() == PacketType.BALANCE_UPDATED));
        assertFalse(c.pushes.stream().anyMatch(packet -> packet.getType() == PacketType.BALANCE_UPDATED || packet.getType() == PacketType.POINT_TRANSFER_RESULT));
        assertEquals(0, ((PageResult<?>) c.request(PacketType.POINT_TRANSFER_HISTORY, new PageRequest(0, 20)).getData()).totalItems());
        a.close();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (sessions.find(alice.userId()).isPresent() && System.nanoTime() < deadline) Thread.sleep(5);
        Wire reconnect = login("alice");
        PointTransferReceipt replay = (PointTransferReceipt) reconnect.request(PacketType.POINT_TRANSFER, gift).getData();
        assertEquals(receipt.transferId(), replay.transferId());
        assertEquals(PacketType.ERROR, reconnect.request(PacketType.POINT_TRANSFER,
                new PointTransferRequest(gift.operationId(), bob.userId(), new BigDecimal("101"), gift.message())).getType());
        assertEquals(1, points.history(alice.userId(), new PageRequest(0, 20)).totalItems());
        assertEquals(new BigDecimal("900.00"), users.getProfile(alice.userId()).balance());
    }

    @Test void requestAcceptTransfersOnceAndUnrelatedPlayerCannotAnswer() throws Exception {
        Wire a = login("alice"), b = login("bob"), c = login("carol");
        a.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1)); b.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
        var create = new PointRequestCreate(id(), bob.userId(), new BigDecimal("125"), "Xin một ít");
        var item = (PointRequestItem) a.request(PacketType.POINT_REQUEST_CREATE, create).getData();
        assertEquals(PacketType.ERROR, c.request(PacketType.POINT_REQUEST_REPLY,
                new PointRequestReply(id(), item.requestId(), PointRequestDecision.ACCEPT)).getType());
        var accept = new PointRequestReply(id(), item.requestId(), PointRequestDecision.ACCEPT);
        Packet accepted = b.request(PacketType.POINT_REQUEST_REPLY, accept);
        assertEquals(PacketType.POINT_TRANSFER_RESULT, accepted.getType());
        var receipt = (PointTransferReceipt) accepted.getData();
        assertEquals(receipt.transferId(), ((PointTransferReceipt) b.request(PacketType.POINT_REQUEST_REPLY, accept).getData()).transferId());
        assertEquals(PointRequestStatus.FULFILLED, points.getRequest(alice.userId(), item.requestId()).status());
        assertEquals(new BigDecimal("1125.00"), users.getProfile(alice.userId()).balance());
        assertEquals(new BigDecimal("875.00"), users.getProfile(bob.userId()).balance());
        assertEquals(PacketType.ERROR, a.request(PacketType.POINT_REQUEST_REPLY,
                new PointRequestReply(id(), item.requestId(), PointRequestDecision.CANCEL)).getType());
        a.request(PacketType.LEAVE_ROOM, null);
        var replay = (PointRequestItem) a.request(PacketType.POINT_REQUEST_CREATE, create).getData();
        assertEquals(item.requestId(), replay.requestId());
        assertEquals(PointRequestStatus.FULFILLED, replay.status());
    }

    @Test void cancelDeclineExpireAndRequestRateLimitDoNotChangeBalances() throws Exception {
        Wire a = login("alice"), b = login("bob");
        a.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1)); b.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1));
        var first = create(a);
        assertEquals(PacketType.ERROR, b.request(PacketType.POINT_REQUEST_CREATE,
                new PointRequestCreate(id(), alice.userId(), BigDecimal.TEN, "reverse duplicate")).getType());
        assertEquals(PointRequestStatus.CANCELLED, ((PointRequestItem) a.request(PacketType.POINT_REQUEST_REPLY,
                new PointRequestReply(id(), first.requestId(), PointRequestDecision.CANCEL)).getData()).status());
        var second = create(a);
        assertEquals(PointRequestStatus.DECLINED, ((PointRequestItem) b.request(PacketType.POINT_REQUEST_REPLY,
                new PointRequestReply(id(), second.requestId(), PointRequestDecision.DECLINE)).getData()).status());
        var third = create(a);
        clock.addAndGet(2001);
        assertEquals(PacketType.ERROR, b.request(PacketType.POINT_REQUEST_REPLY,
                new PointRequestReply(id(), third.requestId(), PointRequestDecision.ACCEPT)).getType());
        assertEquals(PointRequestStatus.EXPIRED, points.getRequest(alice.userId(), third.requestId()).status());
        assertEquals(PacketType.ERROR, a.request(PacketType.POINT_REQUEST_CREATE,
                new PointRequestCreate(id(), bob.userId(), BigDecimal.TEN, "too many")).getType());
        assertEquals(new BigDecimal("1000.00"), users.getProfile(alice.userId()).balance());
        assertEquals(new BigDecimal("1000.00"), users.getProfile(bob.userId()).balance());
    }

    private PointRequestItem create(Wire wire) throws Exception {
        Packet result = wire.request(PacketType.POINT_REQUEST_CREATE, new PointRequestCreate(id(), bob.userId(), BigDecimal.TEN, "please"));
        assertEquals(PacketType.POINT_REQUEST_UPDATED, result.getType());
        return (PointRequestItem) result.getData();
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private Wire login(String name) throws Exception {
        Wire wire = new Wire(); wires.add(wire);
        assertEquals(PacketType.AUTH_RESULT, wire.request(PacketType.LOGIN, new AuthRequest(name, "password123")).getType());
        return wire;
    }
    private final class Wire implements AutoCloseable {
        final Socket socket = new Socket("127.0.0.1", server.getLocalPort());
        final PacketIO.Output out = PacketIO.openOutput(socket);
        final PacketIO.Input in = PacketIO.openInput(socket);
        final List<Packet> pushes = new ArrayList<>();
        Wire() throws Exception { in.setIdleTimeoutMillis(3000); }
        Packet request(PacketType type, Object data) throws Exception {
            Packet request = Packet.request(type, data); PacketIO.write(out, request);
            while (true) {
                Packet packet = PacketIO.read(in);
                if (packet.getType() == PacketType.PING) PacketIO.write(out, new Packet(PacketType.PONG, packet.getRequestId(), null));
                else if (request.getRequestId().equals(packet.getRequestId())) return packet;
                else pushes.add(packet);
            }
        }
        @Override public void close() throws Exception { socket.close(); }
    }
}
