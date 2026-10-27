package com.game3cay.server.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.game3cay.server.bus.JdbcUserBUS;
import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.network.ClientHandler;
import com.game3cay.server.network.SessionRegistry;
import com.game3cay.server.room.RoomEvents;
import com.game3cay.server.room.RoomManager;
import com.game3cay.shared.model.AdminAdjustBalanceRequest;
import com.game3cay.shared.model.AdminLiveMatchDetail;
import com.game3cay.shared.model.AdminLockUserRequest;
import com.game3cay.shared.model.AdminResetRoomRequest;
import com.game3cay.shared.model.AdminRoomDetail;
import com.game3cay.shared.model.AdminStatsSnapshot;
import com.game3cay.shared.model.AdminUserFilterRequest;
import com.game3cay.shared.model.AdminUserItem;
import com.game3cay.shared.model.AuthRequest;
import com.game3cay.shared.model.AuthResult;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.PageResult;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketIO;
import com.game3cay.shared.network.PacketType;
import com.game3cay.shared.security.PasswordUtil;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminSecurityTest {

    private ConnectionProvider connections;
    private JdbcUserBUS userBus;
    private SessionRegistry sessions;
    private RoomManager roomManager;
    private ServerSocket serverSocket;
    private ExecutorService serverExecutor;
    private final List<ClientHandler> handlers = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        String dbUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        connections = () -> DriverManager.getConnection(dbUrl);

        try (Connection conn = connections.open();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "username VARCHAR(50) UNIQUE NOT NULL, "
                    + "password_hash VARCHAR(255) NOT NULL, "
                    + "display_name VARCHAR(100) NOT NULL, "
                    + "email VARCHAR(100) UNIQUE, "
                    + "avatar_url VARCHAR(255), "
                    + "balance DECIMAL(15,2) DEFAULT 1000.00 NOT NULL, "
                    + "status VARCHAR(20) DEFAULT 'ACTIVE', "
                    + "role VARCHAR(20) DEFAULT 'USER', "
                    + "total_games INT DEFAULT 0, "
                    + "total_wins INT DEFAULT 0, "
                    + "total_losses INT DEFAULT 0, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")");

            stmt.execute("CREATE TABLE play_rounds ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "round_id VARCHAR(64) UNIQUE, "
                    + "content_hash VARCHAR(64), "
                    + "runtime_room_id BIGINT, "
                    + "room_name VARCHAR(100), "
                    + "ante DECIMAL(15,2), "
                    + "status VARCHAR(20), "
                    + "ended_at BIGINT"
                    + ")");

            stmt.execute("CREATE TABLE play_ledger ("
                    + "round_id VARCHAR(64) NOT NULL, "
                    + "user_id BIGINT NOT NULL, "
                    + "delta DECIMAL(15,2) NOT NULL, "
                    + "balance_before DECIMAL(15,2) NOT NULL, "
                    + "balance_after DECIMAL(15,2) NOT NULL, "
                    + "PRIMARY KEY (round_id, user_id)"
                    + ")");

            stmt.execute("CREATE TABLE admin_adjustments ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "adjustment_id VARCHAR(64) UNIQUE, "
                    + "admin_user_id BIGINT, "
                    + "target_user_id BIGINT, "
                    + "delta DECIMAL(15,2), "
                    + "balance_before DECIMAL(15,2), "
                    + "balance_after DECIMAL(15,2), "
                    + "reason VARCHAR(255), "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")");

            // Seed user thường và admin
            String userPassHash = PasswordUtil.hash("password123");
            stmt.execute("INSERT INTO users (username, password_hash, display_name, email, balance, status, role) "
                    + "VALUES ('player_one', '" + userPassHash + "', 'Người Chơi 1', 'p1@test.com', 1000.00, 'ACTIVE', 'USER')");

            String adminPassHash = PasswordUtil.hash("adminPass123");
            stmt.execute("INSERT INTO users (username, password_hash, display_name, email, balance, status, role) "
                    + "VALUES ('admin_boss', '" + adminPassHash + "', 'Quản Trị Viên', 'admin@test.com', 5000.00, 'ACTIVE', 'ADMIN')");
        }

        userBus = new JdbcUserBUS(connections);
        sessions = new SessionRegistry();
        roomManager = new RoomManager(
                new RoomEvents() {
                    @Override
                    public void roomUpdated(RoomSnapshot snapshot, List<Long> recipients) {}
                    @Override
                    public void chatMessage(long roomId, ChatMessage message, List<Long> recipients) {}
                    @Override
                    public void packet(Packet packet, List<Long> recipients) {}
                    @Override
                    public void lobbyUpdated(List<RoomSummary> tables) {}
                },
                userBus,
                null
        );

        serverSocket = new ServerSocket(0);
        serverExecutor = Executors.newCachedThreadPool();

        serverExecutor.submit(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, userBus, sessions, roomManager);
                    handlers.add(handler);
                    serverExecutor.submit(handler);
                }
            } catch (Exception ignored) {}
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        for (ClientHandler h : handlers) {
            h.close();
        }
        if (serverSocket != null) {
            serverSocket.close();
        }
        serverExecutor.shutdownNow();
        serverExecutor.awaitTermination(3, TimeUnit.SECONDS);
    }

    @Test
    void normalUserCannotAccessAdminEndpoints() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", serverSocket.getLocalPort());
             TestWire wire = new TestWire(socket)) {

            // Đăng nhập bằng tài khoản USER thông thường
            Packet loginResp = wire.request(PacketType.LOGIN, new AuthRequest("player_one", "password123"));
            assertEquals(PacketType.AUTH_RESULT, loginResp.getType());
            AuthResult auth = (AuthResult) loginResp.getData();
            assertTrue(auth.success());
            assertEquals("USER", auth.role());

            // Thử gọi ADMIN_STATS_REQ
            Packet statsResp = wire.request(PacketType.ADMIN_STATS_REQ, null);
            assertEquals(PacketType.ERROR, statsResp.getType());
            Packet.ErrorPayload err = (Packet.ErrorPayload) statsResp.getData();
            assertEquals(ErrorCode.FORBIDDEN, err.code());

            // Thử gọi ADMIN_USERS_PAGE_REQ
            Packet usersResp = wire.request(PacketType.ADMIN_USERS_PAGE_REQ, new AdminUserFilterRequest("", "ALL", 0, 10));
            assertEquals(PacketType.ERROR, usersResp.getType());
            assertEquals(ErrorCode.FORBIDDEN, ((Packet.ErrorPayload) usersResp.getData()).code());

            // Thử gọi ADMIN_INSPECT_ROOM_REQ
            Packet inspectResp = wire.request(PacketType.ADMIN_INSPECT_ROOM_REQ, 1L);
            assertEquals(PacketType.ERROR, inspectResp.getType());
            assertEquals(ErrorCode.FORBIDDEN, ((Packet.ErrorPayload) inspectResp.getData()).code());
        }
    }

    @Test
    void adminUserCanAccessStatsAndManageUsers() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", serverSocket.getLocalPort());
             TestWire wire = new TestWire(socket)) {

            // Đăng nhập bằng tài khoản ADMIN
            Packet loginResp = wire.request(PacketType.LOGIN, new AuthRequest("admin_boss", "adminPass123"));
            assertEquals(PacketType.AUTH_RESULT, loginResp.getType());
            AuthResult auth = (AuthResult) loginResp.getData();
            assertTrue(auth.success());
            assertEquals("ADMIN", auth.role());

            // 1. Thống kê hệ thống
            Packet statsResp = wire.request(PacketType.ADMIN_STATS_REQ, null);
            assertEquals(PacketType.ADMIN_STATS_RESP, statsResp.getType());
            AdminStatsSnapshot stats = (AdminStatsSnapshot) statsResp.getData();
            assertNotNull(stats);
            assertEquals(1, stats.onlineUsers()); // Chính admin đang online
            assertEquals(2, stats.totalRegisteredUsers()); // player_one và admin_boss
            assertEquals(new BigDecimal("6000.00"), stats.totalBalanceInCirculation());

            // 2. Tra cứu danh sách người dùng
            Packet usersResp = wire.request(PacketType.ADMIN_USERS_PAGE_REQ, new AdminUserFilterRequest("", "ALL", 0, 10));
            assertEquals(PacketType.ADMIN_USERS_PAGE_RESP, usersResp.getType());
            @SuppressWarnings("unchecked")
            PageResult<AdminUserItem> usersPage = (PageResult<AdminUserItem>) usersResp.getData();
            assertEquals(2, usersPage.totalItems());

            // 3. Khóa tài khoản người chơi
            AdminUserItem target = usersPage.items().stream()
                    .filter(u -> "player_one".equals(u.username()))
                    .findFirst()
                    .orElseThrow();

            Packet lockResp = wire.request(PacketType.ADMIN_LOCK_USER_REQ, new AdminLockUserRequest(target.userId(), true));
            assertEquals(PacketType.ACK, lockResp.getType());

            // Kiểm tra trạng thái đã bị khóa trong DB
            assertEquals("BLOCKED", userBus.getProfile(target.userId()).status());

            // 4. Cộng điểm cho người chơi (+500 điểm)
            Packet adjustResp = wire.request(PacketType.ADMIN_ADJUST_BALANCE_REQ,
                    new AdminAdjustBalanceRequest(target.userId(), new BigDecimal("500.00"), "Cộng điểm thưởng"));
            assertEquals(PacketType.ACK, adjustResp.getType());

            // Kiểm tra số dư mới
            assertEquals(new BigDecimal("1500.00"), userBus.getProfile(target.userId()).balance());

            // 5. Giám sát 20 bàn chơi
            Packet roomsResp = wire.request(PacketType.ADMIN_ROOMS_MONITOR_REQ, null);
            assertEquals(PacketType.ADMIN_ROOMS_MONITOR_RESP, roomsResp.getType());
            @SuppressWarnings("unchecked")
            List<AdminRoomDetail> roomDetails = (List<AdminRoomDetail>) roomsResp.getData();
            assertEquals(20, roomDetails.size());

            // 6. Cưỡng chế giải tán / đặt lại bàn #1
            Packet resetResp = wire.request(PacketType.ADMIN_FORCE_RESET_ROOM, new AdminResetRoomRequest(1L, "Reset kiểm thử"));
            assertEquals(PacketType.ACK, resetResp.getType());

            // 7. Soi chi tiết ván bài bàn #1 (God-View)
            Packet inspectResp = wire.request(PacketType.ADMIN_INSPECT_ROOM_REQ, 1L);
            assertEquals(PacketType.ADMIN_INSPECT_ROOM_RESP, inspectResp.getType());
            AdminLiveMatchDetail liveDetail = (AdminLiveMatchDetail) inspectResp.getData();
            assertNotNull(liveDetail);
            assertEquals(1L, liveDetail.roomId());
            assertEquals("WAITING", liveDetail.status());
            assertNotNull(liveDetail.players());

            // 8. Admin bị cấm tham gia bàn chơi (tách biệt hoàn toàn Client - Admin)
            Packet createRoomResp = wire.request(PacketType.CREATE_ROOM, new CreateRoomRequest("Bàn Admin", 2, 8, BigDecimal.ONE));
            assertEquals(PacketType.ERROR, createRoomResp.getType());
            assertEquals(ErrorCode.FORBIDDEN, ((Packet.ErrorPayload) createRoomResp.getData()).code());

            Packet joinRoomResp = wire.request(PacketType.JOIN_ROOM, new JoinRoomRequest(1L));
            assertEquals(PacketType.ERROR, joinRoomResp.getType());
            assertEquals(ErrorCode.FORBIDDEN, ((Packet.ErrorPayload) joinRoomResp.getData()).code());
        }
    }

    private static final class TestWire implements AutoCloseable {
        private final ObjectOutputStream out;
        private final ObjectInputStream in;

        TestWire(Socket socket) throws Exception {
            socket.setSoTimeout(3000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = PacketIO.openInput(socket);
        }

        Packet request(PacketType type, Object data) throws Exception {
            Packet req = Packet.request(type, data);
            PacketIO.write(out, req);

            while (true) {
                Packet incoming = PacketIO.read(in);
                if (incoming.getType() == PacketType.PING) {
                    PacketIO.write(out, new Packet(PacketType.PONG, incoming.getRequestId(), null));
                } else if (req.getRequestId().equals(incoming.getRequestId())) {
                    return incoming;
                }
            }
        }

        @Override
        public void close() throws Exception {
            in.close();
            out.close();
        }
    }
}
