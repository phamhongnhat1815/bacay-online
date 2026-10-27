package com.game3cay.server.network;

import com.game3cay.server.bus.BUSException;
import com.game3cay.server.bus.GameBUS;
import com.game3cay.server.bus.UserBUS;
import com.game3cay.server.bus.UserDTO;
import com.game3cay.server.room.RoomManager;
import com.game3cay.shared.model.AdminAdjustBalanceRequest;
import com.game3cay.shared.model.AdminBroadcastRequest;
import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.AdminLiveMatchDetail;
import com.game3cay.shared.model.AdminLockUserRequest;
import com.game3cay.shared.model.AdminResetRoomRequest;
import com.game3cay.shared.model.AdminRoomDetail;
import com.game3cay.shared.model.AdminStatsSnapshot;
import com.game3cay.shared.model.AdminUserFilterRequest;
import com.game3cay.shared.model.AdminUserItem;
import com.game3cay.shared.model.AuthRequest;
import com.game3cay.shared.model.AuthResult;
import com.game3cay.shared.model.ChangePasswordRequest;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.GameActionRequest;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.PageRequest;
import com.game3cay.shared.model.PageResult;
import com.game3cay.shared.model.ProfileResult;
import com.game3cay.shared.model.RegisterRequest;
import com.game3cay.shared.model.RoomChatEvent;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.UpdateProfileRequest;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketIO;
import com.game3cay.shared.network.PacketType;
import com.game3cay.shared.network.RuntimeSettings;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Xử lý phiên kết nối mạng TCP của một Client kết nối tới Server.
 * <p>
 * Mỗi kết nối có:
 * <ul>
 *   <li>1 luồng nhận dữ liệu đọc từ socket (trong phương thức {@link #run()}).</li>
 *   <li>1 luồng đơn gửi dữ liệu chuyên biệt ({@code sender}) với hàng đợi tối đa 128 gói tin.</li>
 *   <li>1 bộ hẹn giờ nhịp tim định kỳ ({@code heartbeat}) kiểm tra tình trạng kết nối.</li>
 *   <li>Bộ đệm LRU phản hồi (128 mục) hỗ trợ Request-Response và kiểm tra lặp.</li>
 * </ul>
 */
public final class ClientHandler implements Runnable {

    private static final int HEARTBEAT_INTERVAL = RuntimeSettings.integer(
            "server.properties", "heartbeat.intervalSeconds", 5, 1, 60);

    private static final int HEARTBEAT_TIMEOUT = RuntimeSettings.integer(
            "server.properties", "heartbeat.timeoutSeconds", 15, 2, 300);

    private final Socket socket;
    private final UserBUS users;
    private final SessionRegistry sessions;
    private final RoomManager rooms;
    private final GameBUS games;

    private final Map<String, Packet> replies = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Packet> entry) {
                    return size() > 128;
                }
            });

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong lastPong = new AtomicLong();
    private final AtomicReference<String> waitingPong = new AtomicReference<>();

    private final ExecutorService sender = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128),
            task -> createDaemonThread(task, "server-sender")
    );

    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(task -> createDaemonThread(task, "server-heartbeat"));

    private ObjectOutputStream output;
    private UserDTO currentUser;

    public ClientHandler(Socket socket, UserBUS users, SessionRegistry sessions, RoomManager rooms) {
        this(socket, users, sessions, rooms, null);
    }

    public ClientHandler(
            Socket socket,
            UserBUS users,
            SessionRegistry sessions,
            RoomManager rooms,
            GameBUS games
    ) {
        this.socket = socket;
        this.users = users;
        this.sessions = sessions;
        this.rooms = rooms;
        this.games = games;
    }

    @Override
    public void run() {
        try (socket) {
            // Đặt timeout 5s để khởi tạo bắt tay luồng Object
            socket.setSoTimeout(5000);

            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            ObjectInputStream input = PacketIO.openInput(socket);
            socket.setSoTimeout(0); // Chuyển sang chế độ không timeout (heartbeat quản lý)

            lastPong.set(System.nanoTime());
            send(new Packet(PacketType.SERVER_NOTICE, null, "Đã kết nối máy chủ Ba Cây, giao thức v" + Packet.VERSION));

            heartbeat.scheduleAtFixedRate(this::checkHeartbeat, 0, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

            while (!closed.get()) {
                handlePacket(PacketIO.read(input));
            }
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            // Kết thúc phiên hoặc ngắt kết nối
        } finally {
            close();
            clearSession();
        }
    }

    private void checkHeartbeat() {
        if (System.nanoTime() - lastPong.get() > TimeUnit.SECONDS.toNanos(HEARTBEAT_TIMEOUT)) {
            close();
            return;
        }

        Packet ping = Packet.request(PacketType.PING, null);
        if (waitingPong.compareAndSet(null, ping.getRequestId())) {
            send(ping);
        }
    }

    private void handlePacket(Packet packet) {
        if (packet.getType() == PacketType.PONG) {
            String expected = waitingPong.get();
            if (expected != null
                    && expected.equals(packet.getRequestId())
                    && waitingPong.compareAndSet(expected, null)) {
                lastPong.set(System.nanoTime());
            }
            return;
        }

        String requestId = packet.getRequestId();
        if (requestId == null) {
            sendError(null, ErrorCode.INVALID_REQUEST, "Yêu cầu phải có mã requestId");
            return;
        }

        Packet previousReply = replies.get(requestId);
        if (previousReply != null) {
            send(previousReply);
            return;
        }

        try {
            switch (packet.getType()) {
                case ECHO_REQUEST -> sendAck(requestId);

                case REGISTER -> {
                    if (currentUser != null) {
                        throw fail(ErrorCode.INVALID_STATE, "Vui lòng đăng xuất trước khi đăng ký tài khoản mới");
                    }
                    RegisterRequest req = (RegisterRequest) packet.getData();
                    UserDTO created = requireUserBUS().register(
                            req.username(),
                            req.password(),
                            req.displayName(),
                            req.email()
                    );
                    sendAuthResult(requestId, created);
                }

                case LOGIN -> {
                    if (currentUser != null) {
                        throw fail(ErrorCode.ALREADY_LOGGED_IN, "Kết nối này đã đăng nhập tài khoản");
                    }
                    AuthRequest req = (AuthRequest) packet.getData();
                    UserDTO authenticated = requireUserBUS().authenticate(req.username(), req.password());

                    if (!sessions.bind(authenticated.userId(), this)) {
                        throw fail(ErrorCode.ALREADY_LOGGED_IN, "Tài khoản đang đăng nhập ở một kết nối khác");
                    }

                    currentUser = authenticated;
                    sendAuthResult(requestId, currentUser);
                }

                case LOGOUT -> {
                    requireAuthenticatedUser();
                    rooms.onDisconnected(currentUser.userId());
                    clearSession();
                    sendAck(requestId);
                }

                case ROOM_LIST -> {
                    requireAuthenticatedUser();
                    send(new Packet(PacketType.ROOM_LIST_RESULT, requestId, rooms.listRooms()));
                }

                case CREATE_ROOM -> {
                    UserDTO user = requireUserBUS().getProfile(requirePlayerUser().userId());
                    RoomSnapshot snapshot = rooms.create(user, (CreateRoomRequest) packet.getData());
                    send(new Packet(PacketType.ROOM_UPDATED, requestId, snapshot));
                }

                case JOIN_ROOM -> {
                    UserDTO user = requireUserBUS().getProfile(requirePlayerUser().userId());
                    RoomSnapshot snapshot = rooms.join(user, (JoinRoomRequest) packet.getData());
                    send(new Packet(PacketType.ROOM_UPDATED, requestId, snapshot));
                }

                case LEAVE_ROOM -> {
                    rooms.leave(requireAuthenticatedUser().userId());
                    sendAck(requestId);
                }

                case SET_READY -> {
                    RoomSnapshot snapshot = rooms.setReady(
                            requirePlayerUser().userId(),
                            (Boolean) packet.getData()
                    );
                    send(new Packet(PacketType.ROOM_UPDATED, requestId, snapshot));
                }

                case CHANGE_ROOM_BET -> {
                    RoomSnapshot snapshot = rooms.changeBet(
                            requirePlayerUser().userId(),
                            (BigDecimal) packet.getData()
                    );
                    send(new Packet(PacketType.ROOM_UPDATED, requestId, snapshot));
                }

                case CHAT_MESSAGE -> {
                    UserDTO actor = requirePlayerUser();
                    if (!(packet.getData() instanceof ChatMessage message) || !"TEXT".equals(message.type())) {
                        throw fail(ErrorCode.INVALID_REQUEST, "Client chỉ được gửi tin nhắn loại TEXT");
                    }
                    rooms.chat(actor.userId(), message.content());
                    sendAck(requestId);
                }

                case START_GAME_REQUEST -> {
                    rooms.start(requirePlayerUser().userId());
                    sendAck(requestId);
                }

                case GAME_ACTION -> {
                    rooms.action(requirePlayerUser().userId(), (GameActionRequest) packet.getData());
                    sendAck(requestId);
                }

                case PLAY_AGAIN_VOTE -> {
                    rooms.setReady(requirePlayerUser().userId(), (Boolean) packet.getData());
                    sendAck(requestId);
                }

                case GET_PROFILE -> sendProfile(requestId, requireAuthenticatedUser().userId());

                case UPDATE_PROFILE -> {
                    long userId = requireAuthenticatedUser().userId();
                    if (rooms.inRoom(userId)) {
                        throw fail(ErrorCode.INVALID_STATE, "Vui lòng rời bàn chơi trước khi cập nhật hồ sơ");
                    }
                    UpdateProfileRequest req = (UpdateProfileRequest) packet.getData();
                    requireUserBUS().updateProfile(userId, req.displayName(), req.email(), req.avatarUrl());
                    currentUser = requireUserBUS().getProfile(userId);
                    sendProfile(requestId, userId);
                }

                case CHANGE_PASSWORD -> {
                    ChangePasswordRequest req = (ChangePasswordRequest) packet.getData();
                    requireUserBUS().changePassword(requireAuthenticatedUser().userId(), req.currentPassword(), req.newPassword());
                    sendAck(requestId);
                }

                case GET_HISTORY -> {
                    long userId = requireAuthenticatedUser().userId();
                    PageRequest req = (PageRequest) packet.getData();
                    if (games == null) {
                        throw fail(ErrorCode.INVALID_STATE, "Chưa cấu hình dữ liệu lịch sử ván");
                    }
                    send(new Packet(PacketType.HISTORY_RESULT, requestId, games.getHistory(userId, req.page(), req.pageSize())));
                }

                case GET_LEADERBOARD -> {
                    requireAuthenticatedUser();
                    PageRequest req = (PageRequest) packet.getData();
                    if (games == null) {
                        throw fail(ErrorCode.INVALID_STATE, "Chưa cấu hình dữ liệu bảng xếp hạng");
                    }
                    send(new Packet(PacketType.LEADERBOARD_RESULT, requestId, games.getLeaderboard(req.page(), req.pageSize())));
                }

                case ADMIN_STATS_REQ -> {
                    requireAdminUser();
                    int activeRooms = rooms.getActiveRoomCount();
                    long onlineUsers = sessions.onlineCount();
                    AdminStatsSnapshot stats = requireUserBUS().getSystemStatsForAdmin(onlineUsers, activeRooms);
                    send(new Packet(PacketType.ADMIN_STATS_RESP, requestId, stats));
                }

                case ADMIN_USERS_PAGE_REQ -> {
                    requireAdminUser();
                    AdminUserFilterRequest req = (AdminUserFilterRequest) packet.getData();
                    PageResult<AdminUserItem> usersPage = requireUserBUS().findUsersForAdmin(
                            req.query(), req.statusFilter(), req.page(), req.pageSize());
                    send(new Packet(PacketType.ADMIN_USERS_PAGE_RESP, requestId, usersPage));
                }

                case ADMIN_LOCK_USER_REQ -> {
                    requireAdminUser();
                    AdminLockUserRequest req = (AdminLockUserRequest) packet.getData();
                    requireUserBUS().updateUserStatusForAdmin(req.userId(), req.blocked());
                    if (req.blocked()) {
                        sessions.find(req.userId()).ifPresent(ClientHandler::close);
                    }
                    sendAck(requestId);
                }

                case ADMIN_ADJUST_BALANCE_REQ -> {
                    UserDTO admin = requireAdminUser();
                    AdminAdjustBalanceRequest req = (AdminAdjustBalanceRequest) packet.getData();
                    requireUserBUS().adjustBalanceForAdmin(admin.userId(), req.userId(), req.delta(), req.reason());
                    sendAck(requestId);
                }

                case ADMIN_ROOMS_MONITOR_REQ -> {
                    requireAdminUser();
                    List<AdminRoomDetail> roomDetails = rooms.getAdminRoomDetails();
                    send(new Packet(PacketType.ADMIN_ROOMS_MONITOR_RESP, requestId, roomDetails));
                }

                case ADMIN_FORCE_RESET_ROOM -> {
                    requireAdminUser();
                    AdminResetRoomRequest req = (AdminResetRoomRequest) packet.getData();
                    rooms.forceResetRoom(req.roomId(), req.reason());
                    sendAck(requestId);
                }

                case ADMIN_INSPECT_ROOM_REQ -> {
                    requireAdminUser();
                    Long roomId = (Long) packet.getData();
                    AdminLiveMatchDetail detail = rooms.getAdminRoomLiveDetail(roomId);
                    send(new Packet(PacketType.ADMIN_INSPECT_ROOM_RESP, requestId, detail));
                }

                case ADMIN_BROADCAST_REQ -> {
                    UserDTO admin = requireAdminUser();
                    AdminBroadcastRequest req = (AdminBroadcastRequest) packet.getData();
                    String prefix = "URGENT".equalsIgnoreCase(req.level()) ? "[KHẨN CẤP] " : "[THÔNG BÁO] ";
                    ChatMessage broadcastMsg = new ChatMessage(
                            admin.userId(),
                            "HỆ THỐNG",
                            prefix + req.message(),
                            "SYSTEM",
                            System.currentTimeMillis()
                    );
                    sessions.broadcastAll(Packet.serverPush(PacketType.CHAT_SYSTEM, new RoomChatEvent(0L, broadcastMsg)));
                    sendAck(requestId);
                }

                case ADMIN_LEDGER_PAGE_REQ -> {
                    requireAdminUser();
                    PageRequest req = (PageRequest) packet.getData();
                    PageResult<AdminLedgerItem> ledgerPage = requireUserBUS().getLedgerForAdmin(req.page(), req.pageSize());
                    send(new Packet(PacketType.ADMIN_LEDGER_PAGE_RESP, requestId, ledgerPage));
                }

                default -> sendError(requestId, ErrorCode.INVALID_REQUEST, "Loại thông điệp không được gửi từ phía client");
            }
        } catch (BUSException e) {
            sendError(requestId, e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            sendError(requestId, ErrorCode.INTERNAL_ERROR, "Không thể xử lý yêu cầu");
        }
    }

    private void sendProfile(String requestId, long userId) throws BUSException {
        UserDTO u = requireUserBUS().getProfile(userId);
        ProfileResult profile = new ProfileResult(
                u.userId(),
                u.username(),
                u.displayName(),
                u.email(),
                u.avatarUrl(),
                u.balance(),
                u.totalGames(),
                u.totalWins(),
                u.totalLosses()
        );
        send(new Packet(PacketType.PROFILE_RESULT, requestId, profile));
    }

    private UserBUS requireUserBUS() throws BUSException {
        if (users == null) {
            throw fail(ErrorCode.INTERNAL_ERROR, "Máy chủ chưa được cấu hình dịch vụ tài khoản");
        }
        return users;
    }

    private UserDTO requireAuthenticatedUser() throws BUSException {
        if (currentUser == null) {
            throw fail(ErrorCode.UNAUTHENTICATED, "Bạn cần đăng nhập để thực hiện chức năng này");
        }
        return currentUser;
    }

    private UserDTO requireAdminUser() throws BUSException {
        UserDTO user = requireAuthenticatedUser();
        if (!"ADMIN".equalsIgnoreCase(user.role())) {
            throw fail(ErrorCode.FORBIDDEN, "Thao tác chỉ dành cho tài khoản Quản trị viên (ADMIN)");
        }
        return user;
    }

    private UserDTO requirePlayerUser() throws BUSException {
        UserDTO user = requireAuthenticatedUser();
        if ("ADMIN".equalsIgnoreCase(user.role())) {
            throw fail(ErrorCode.FORBIDDEN, "Tài khoản Quản trị viên (ADMIN) chỉ dùng để quản trị, không được phép thao tác bàn chơi");
        }
        return user;
    }

    private void sendAuthResult(String requestId, UserDTO u) {
        AuthResult result = new AuthResult(
                true,
                u.userId(),
                u.username(),
                u.displayName(),
                u.balance(),
                u.role(),
                null,
                "Thành công"
        );
        send(new Packet(PacketType.AUTH_RESULT, requestId, result));
    }

    private void clearSession() {
        if (currentUser == null) {
            return;
        }

        UserDTO oldUser = currentUser;
        currentUser = null;

        rooms.onDisconnected(oldUser.userId());
        sessions.release(oldUser.userId(), this);
    }

    private void sendAck(String requestId) {
        send(new Packet(PacketType.ACK, requestId, null));
    }

    private void sendError(String requestId, ErrorCode code, String message) {
        send(new Packet(PacketType.ERROR, requestId, new Packet.ErrorPayload(code, message)));
    }

    private static BUSException fail(ErrorCode code, String message) {
        return new BUSException(code, message);
    }

    /**
     * Đưa gói tin vào hàng đợi gửi dữ liệu cho client.
     *
     * @param packet gói tin cần gửi
     */
    public void send(Packet packet) {
        if (packet.getRequestId() != null && packet.getType() != PacketType.PING) {
            replies.put(packet.getRequestId(), packet);
        }

        if (closed.get()) {
            return;
        }

        try {
            sender.execute(() -> {
                if (closed.get()) {
                    return;
                }
                try {
                    PacketIO.write(output, packet);
                } catch (IOException | RuntimeException e) {
                    close();
                }
            });
        } catch (RejectedExecutionException e) {
            close();
        }
    }

    /**
     * Đóng kết nối an toàn và giải phóng tài nguyên luồng.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {
            // Bỏ qua ngoại lệ đóng socket
        }

        heartbeat.shutdownNow();
        sender.shutdownNow();
    }

    public boolean isClosed() {
        return closed.get();
    }

    private static Thread createDaemonThread(Runnable task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        return thread;
    }
}
