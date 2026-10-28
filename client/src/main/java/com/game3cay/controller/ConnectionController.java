package com.game3cay.controller;

import com.game3cay.gui.DataDialog;

import com.game3cay.gui.MainFrame;
import com.game3cay.gui.Sound;
import com.game3cay.network.PacketListener;
import com.game3cay.network.SocketClient;
import com.game3cay.shared.model.AuthRequest;
import com.game3cay.shared.model.AuthResult;
import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.ChangePasswordRequest;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.GameActionRequest;
import com.game3cay.shared.model.GameStartInfo;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.PageRequest;
import com.game3cay.shared.model.PageResult;
import com.game3cay.shared.model.PlayAgainPrompt;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.model.ProfileResult;
import com.game3cay.shared.model.RegisterRequest;
import com.game3cay.shared.model.RoomChatEvent;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.model.RoundResult;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketType;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ConnectionController implements PacketListener {
    private final MainFrame view;
    private final Timer requestTimeoutTimer;
    private final Timer maintenanceTimer;

    private SocketClient client;
    private AuthResult currentUser;
    private RoomSnapshot currentRoom;

    private record PendingRequest(String id, PacketType type, long roomId, boolean silent) {}

    private PendingRequest pendingRequest;
    private Packet queuedAuthPacket;
    private AuthRequest afterRegisterLogin;
    private String connectedHost;
    private int connectedPort;
    private boolean isClosing;
    private long lastRoomsRefreshTime;
    private long lastProfileRefreshTime;
    private DataDialog dataDialog;
    private final List<RoomChatEvent> pendingRoomChatEvents = new ArrayList<>();

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private final Timer reconnectTimer;
    private String savedUsername;
    private char[] savedPassword;
    private char[] pendingNewPassword;
    private String savedHost;
    private int savedPort;
    private long savedRoomId;
    private boolean isExplicitLogout;
    private final ReconnectState reconnectState = new ReconnectState(MAX_RECONNECT_ATTEMPTS);

    public ConnectionController(MainFrame view) {
        this.view = Objects.requireNonNull(view, "view không được null");

        this.requestTimeoutTimer = new Timer(
                12000,
                e -> {
                    if (reconnectState.active()) {
                        scheduleReconnect("Máy chủ chưa phản hồi khi khôi phục phiên.");
                        return;
                    }
                    if (currentUser != null && savedPassword != null && !isExplicitLogout) {
                        startAutoReconnect();
                    } else {
                        handleDisconnect("Máy chủ chưa phản hồi. Vui lòng đăng nhập lại để tiếp tục.");
                    }
                }
        );
        this.requestTimeoutTimer.setRepeats(false);

        this.reconnectTimer = new Timer(1500, e -> attemptReconnect());
        this.reconnectTimer.setRepeats(false);

        this.maintenanceTimer = new Timer(1000, e -> performMaintenance());
        this.maintenanceTimer.start();

        initViewListeners();
        updateUiOperationState();
    }

    private void initViewListeners() {
        view.onSubmit(this::handleAuthSubmit);
        view.onLogout(() -> {
            if (view.confirmLeave()) {
                isExplicitLogout = true;
                sendRequest(PacketType.LOGOUT, null);
            }
        });

        view.lobby().onJoin(roomId -> {
            if (currentUser != null && currentRoom == null) {
                sendRequest(PacketType.JOIN_ROOM, new JoinRoomRequest(roomId));
            }
        });

        view.room().onLeave(() -> {
            if (currentRoom != null && view.confirmLeave()) {
                savedRoomId = 0;
                sendRequest(PacketType.LEAVE_ROOM, null);
            }
        });

        view.room().onReady(this::toggleReadyState);
        view.room().onChangeBet(amount -> sendRequest(PacketType.CHANGE_ROOM_BET, amount));
        view.room().onStart(() -> sendRequest(PacketType.START_GAME_REQUEST, null));

        view.room().onSend(() -> {
            String text = view.room().messageText();
            if (text.isBlank() || text.length() > 500) {
                view.showMessage("Nội dung tin nhắn từ 1 đến 500 ký tự.");
                return;
            }
            sendRequest(PacketType.CHAT_MESSAGE, new ChatMessage(null, null, text, "TEXT", 0));
        });

        view.room().game().onAction(actionReq -> sendRequest(PacketType.GAME_ACTION, actionReq));

        view.onProfile(() -> sendRequest(PacketType.GET_PROFILE, null));
        view.onHistory(() -> openDataDialog(true));
        view.onLeaderboard(() -> openDataDialog(false));
        view.onChangePassword(req -> {
            if (pendingNewPassword != null) {
                Arrays.fill(pendingNewPassword, '\0');
            }
            pendingNewPassword = req.newPassword().toCharArray();
            sendRequest(PacketType.CHANGE_PASSWORD, req);
        });

        view.onClose(() -> {
            if (!view.confirmLeave()) {
                return;
            }
            isClosing = true;
            isExplicitLogout = true;
            reconnectTimer.stop();
            maintenanceTimer.stop();
            requestTimeoutTimer.stop();
            if (client != null) {
                client.disconnect();
            }
            queuedAuthPacket = null;
            afterRegisterLogin = null;
            if (savedPassword != null) {
                Arrays.fill(savedPassword, '\0');
                savedPassword = null;
            }
            if (pendingNewPassword != null) {
                Arrays.fill(pendingNewPassword, '\0');
                pendingNewPassword = null;
            }
            view.dispose();
        });
    }

    private void handleAuthSubmit() {
        if (currentUser != null || pendingRequest != null || queuedAuthPacket != null) {
            return;
        }

        char[] passwordChars = view.getPassword();
        char[] confirmChars = view.getConfirmPassword();

        try {
            String username = view.getUsername();
            String password = new String(passwordChars);

            if (username.isBlank() || username.length() > 50 || password.isBlank() || password.length() > 128) {
                view.showMessage("Vui lòng nhập tên tài khoản và mật khẩu hợp lệ.");
                return;
            }

            if (view.isRegisterMode()) {
                if (!username.matches("^[a-zA-Z0-9_]{3,50}$")) {
                    view.showMessage("Tên tài khoản: 3–50 ký tự (chữ không dấu, số hoặc _).");
                    return;
                }
                if (password.length() < 8) {
                    view.showMessage("Mật khẩu cần ít nhất 8 ký tự.");
                    return;
                }
                if (!Arrays.equals(passwordChars, confirmChars)) {
                    view.showMessage("Hai mật khẩu không khớp nhau.");
                    return;
                }
                if (view.getDisplayName().length() > 100 || view.getEmail().length() > 100) {
                    view.showMessage("Tên hiển thị và email không được vượt quá 100 ký tự.");
                    return;
                }
                if (!view.getEmail().isBlank() && !view.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    view.showMessage("Địa chỉ email không đúng định dạng.");
                    return;
                }

                afterRegisterLogin = new AuthRequest(username, password);
                queuedAuthPacket = Packet.request(
                        PacketType.REGISTER,
                        new RegisterRequest(
                                username,
                                password,
                                optionalText(view.getDisplayName()),
                                optionalText(view.getEmail())
                        )
                );
            } else {
                queuedAuthPacket = Packet.request(PacketType.LOGIN, new AuthRequest(username, password));
            }

            if (savedPassword != null) {
                Arrays.fill(savedPassword, '\0');
            }
            savedPassword = Arrays.copyOf(passwordChars, passwordChars.length);
            savedUsername = username;
            savedHost = view.getHost();
            savedPort = view.getPort();
            isExplicitLogout = false;

            view.clearPasswords();
            view.showMessage("Đang kết nối tới máy chủ…");
            updateUiOperationState();
            requestTimeoutTimer.restart();

            if (client != null && (!view.getHost().equals(connectedHost) || view.getPort() != connectedPort)) {
                SocketClient oldClient = client;
                client = null;
                oldClient.disconnect();
            }

            if (client != null && client.isConnected()) {
                sendQueuedAuth();
            } else {
                client = new SocketClient(this);
                connectedHost = view.getHost();
                connectedPort = view.getPort();
                client.connect(connectedHost, connectedPort);
            }
        } finally {
            Arrays.fill(passwordChars, '\0');
            Arrays.fill(confirmChars, '\0');
        }
    }

    private static String optionalText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private void sendQueuedAuth() {
        Packet packet = queuedAuthPacket;
        queuedAuthPacket = null;
        if (packet != null) {
            dispatchPacket(packet, false);
        }
    }

    private void toggleReadyState() {
        if (currentRoom == null || currentUser == null || !"WAITING".equals(currentRoom.status())) {
            return;
        }

        currentRoom.players().stream()
                .filter(p -> p.userId() == currentUser.userId())
                .findFirst()
                .ifPresent(p -> sendRequest(PacketType.SET_READY, !p.ready()));
    }

    private void sendRequest(PacketType type, Object data) {
        sendRequest(type, data, false);
    }

    private void sendRequest(PacketType type, Object data, boolean silent) {
        if (pendingRequest != null || queuedAuthPacket != null) {
            return;
        }
        if (client == null || !client.isConnected()) {
            if (currentUser != null && savedPassword != null && !isExplicitLogout) {
                if (reconnectState.active()) scheduleReconnect("Kết nối chưa sẵn sàng.");
                else startAutoReconnect();
            } else {
                handleDisconnect("Kết nối máy chủ đã ngắt. Vui lòng đăng nhập lại.");
            }
            return;
        }
        dispatchPacket(Packet.request(type, data), silent);
    }

    private void dispatchPacket(Packet packet, boolean silent) {
        long targetRoomId = (packet.getData() instanceof JoinRoomRequest j)
                ? j.roomId()
                : (currentRoom == null ? -1 : currentRoom.roomId());

        pendingRequest = new PendingRequest(packet.getRequestId(), packet.getType(), targetRoomId, silent);
        if (packet.getType() == PacketType.JOIN_ROOM) {
            pendingRoomChatEvents.clear();
        }

        if (!client.send(packet)) {
            if (currentUser != null && savedPassword != null && !isExplicitLogout) {
                if (reconnectState.active()) scheduleReconnect("Không gửi được yêu cầu khôi phục phiên.");
                else startAutoReconnect();
            } else {
                handleDisconnect("Không gửi được dữ liệu. Vui lòng đăng nhập lại.");
            }
            return;
        }

        updateUiOperationState();
        requestTimeoutTimer.restart();
    }

    private void performMaintenance() {
        if (currentUser == null || pendingRequest != null || currentRoom != null || reconnectState.active()) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastRoomsRefreshTime >= TimeUnit.SECONDS.toNanos(10)) {
            loadRoomList(true);
            return;
        }

        if (now - lastProfileRefreshTime >= TimeUnit.SECONDS.toNanos(5)) {
            lastProfileRefreshTime = now;
            sendRequest(PacketType.GET_PROFILE, null, true);
        }
    }

    private void loadRoomList(boolean silent) {
        lastRoomsRefreshTime = System.nanoTime();
        sendRequest(PacketType.ROOM_LIST, null, silent);
    }

    private void openDataDialog(boolean isHistory) {
        if (currentUser == null || pendingRequest != null) {
            return;
        }

        if (dataDialog != null) {
            dataDialog.dispose();
        }

        dataDialog = new DataDialog(view, isHistory ? "Lịch sử của tôi" : "Bảng xếp hạng");
        dataDialog.onLoad(page -> {
            if (pendingRequest != null) {
                dataDialog.failure("Đang có thao tác chờ, vui lòng thử lại sau.");
                return;
            }
            sendRequest(isHistory ? PacketType.GET_HISTORY : PacketType.GET_LEADERBOARD, new PageRequest(page, 20));
        });

        dataDialog.setVisible(true);
        sendRequest(isHistory ? PacketType.GET_HISTORY : PacketType.GET_LEADERBOARD, new PageRequest(0, 20));
    }

    @Override
    public void onConnected(SocketClient source) {
        runOnSwingThread(source, () -> {
            if (reconnectState.active() && savedUsername != null && savedPassword != null) {
                queuedAuthPacket = Packet.request(
                        PacketType.LOGIN,
                        new AuthRequest(savedUsername, new String(savedPassword))
                );
                sendQueuedAuth();
            } else {
                sendQueuedAuth();
            }
        });
    }

    @Override
    public void onPacketReceived(SocketClient source, Packet packet) {
        runOnSwingThread(source, () -> handleIncomingPacket(packet));
    }

    @Override
    public void onDisconnected(SocketClient source, String reason) {
        runOnSwingThread(source, () -> {
            if (isClosing || isExplicitLogout) {
                handleDisconnect(
                        currentUser == null
                                ? "Không thể kết nối máy chủ. Vui lòng kiểm tra địa chỉ và đăng nhập lại."
                                : "Đã đăng xuất tài khoản."
                );
                return;
            }

            if (reconnectState.active()) {
                scheduleReconnect(reason);
                return;
            }

            if (currentUser != null && savedPassword != null) {
                startAutoReconnect();
            } else {
                handleDisconnect(
                        currentUser == null
                                ? "Không thể kết nối máy chủ. Vui lòng kiểm tra địa chỉ và đăng nhập lại."
                                : "Kết nối bị gián đoạn. Vui lòng đăng nhập lại để tiếp tục."
                );
            }
        });
    }

    private void startAutoReconnect() {
        if (reconnectState.active() || isClosing || isExplicitLogout) {
            return;
        }
        reconnectState.begin();
        pendingRequest = null;
        queuedAuthPacket = null;
        requestTimeoutTimer.stop();
        updateUiOperationState();
        view.showMessage("⚠️ Mất kết nối. Đang khôi phục kết nối... (Lần 1/" + MAX_RECONNECT_ATTEMPTS + ")");
        attemptReconnect();
    }

    private void attemptReconnect() {
        if (!reconnectState.active() || isClosing || isExplicitLogout) {
            reconnectTimer.stop();
            return;
        }

        if (!reconnectState.nextAttempt()) {
            reconnectTimer.stop();
            handleDisconnect("Mất kết nối với máy chủ sau " + MAX_RECONNECT_ATTEMPTS + " lần thử. Vui lòng đăng nhập lại.");
            return;
        }

        view.showMessage("⚠️ Đang khôi phục kết nối... (Lần " + reconnectState.attempts() + "/" + MAX_RECONNECT_ATTEMPTS + ")");

        if (client != null) {
            SocketClient oldClient = client;
            client = null;
            oldClient.disconnect();
        }

        client = new SocketClient(this);
        connectedHost = savedHost;
        connectedPort = savedPort;
        requestTimeoutTimer.restart();
        client.connect(savedHost, savedPort);
    }

    private void scheduleReconnect(String reason) {
        if (!reconnectState.active() || isClosing || isExplicitLogout) return;
        pendingRequest = null;
        queuedAuthPacket = null;
        requestTimeoutTimer.stop();
        SocketClient oldClient = client;
        client = null;
        if (oldClient != null) oldClient.disconnect();
        updateUiOperationState();
        view.showMessage(reason + " Đang chờ thử kết nối lại…");
        reconnectTimer.restart();
    }

    private void handleReconnectFailure(ErrorCode code, String message) {
        if (ReconnectState.retryable(code)) {
            scheduleReconnect(message);
        } else {
            handleDisconnect(message + " Vui lòng đăng nhập lại.");
        }
    }

    private void handleIncomingPacket(Packet packet) {
        PacketType type = packet.getType();
        String requestId = packet.getRequestId();
        Object data = packet.getData();

        if (type == PacketType.PING || type == PacketType.SERVER_NOTICE) {
            return;
        }

        if (requestId == null) {
            if (currentUser == null) {
                return;
            }

            if (type == PacketType.ROOM_LIST_RESULT) {
                updateRoomListInView(data);
                return;
            }

            if (type == PacketType.ROOM_UPDATED) {
                RoomSnapshot snapshot = (RoomSnapshot) data;
                boolean entering = pendingRequest != null
                        && pendingRequest.type() == PacketType.JOIN_ROOM
                        && pendingRequest.roomId() == snapshot.roomId();
                applyRoomSnapshot(snapshot, entering);
                return;
            }

            if (type == PacketType.LEAVE_ROOM) {
                currentRoom = null;
                view.showLobby();
                updateUiOperationState();
                loadRoomList(false);
                String reason = (data instanceof String s && !s.isBlank()) ? s : "Bạn đã quay về sảnh chơi.";
                view.showMessage(reason);
                return;
            }

            if (type == PacketType.CHAT_MESSAGE || type == PacketType.CHAT_SYSTEM) {
                RoomChatEvent chatEvent = (RoomChatEvent) data;
                boolean isGlobal = chatEvent.roomId() == 0L;
                if (isGlobal) {
                    if (currentRoom != null) {
                        view.room().appendChat(chatEvent.message());
                    }
                    view.showMessage(chatEvent.message().content());
                } else if (currentRoom != null && chatEvent.roomId() == currentRoom.roomId()) {
                    view.room().appendChat(chatEvent.message());
                    if (chatEvent.message().content().contains("úp bài")) {
                        Sound.playCardFlip();
                    }
                } else if (pendingRequest != null
                        && pendingRequest.type() == PacketType.JOIN_ROOM
                        && pendingRoomChatEvents.size() < 16) {
                    pendingRoomChatEvents.add(chatEvent);
                }
                return;
            }

            if (currentRoom == null) {
                return;
            }

            var gamePanel = view.room().game();
            switch (type) {
                case START_GAME -> {
                    GameStartInfo startInfo = (GameStartInfo) data;
                    if (startInfo.roomId() == currentRoom.roomId()) {
                        gamePanel.start(startInfo.roundId(), currentUser.userId());
                        view.showMessage("Ván mới đã bắt đầu. Chúc bạn may mắn!");
                    }
                }
                case GAME_UPDATED -> {
                    GameStateSnapshot snapshot = (GameStateSnapshot) data;
                    // A full resync can arrive after the START_GAME event was missed.
                    if (!gamePanel.accepts(snapshot.roundId()) && "PLAYING".equals(currentRoom.status())) {
                        gamePanel.start(snapshot.roundId(), currentUser.userId());
                    }
                    gamePanel.game(snapshot);
                }
                case BET_UPDATED -> gamePanel.bets((BetStateSnapshot) data);
                case PLAYER_STATE -> {
                    PlayerStateSnapshot playerSnapshot = (PlayerStateSnapshot) data;
                    if (gamePanel.accepts(playerSnapshot.roundId())) {
                        gamePanel.mine(playerSnapshot);
                        view.setBalance(playerSnapshot.myBalance());
                    }
                }
                case ROUND_RESULT -> {
                    gamePanel.result((RoundResult) data);
                    lastProfileRefreshTime = System.nanoTime();
                    view.showMessage("Kết quả hiển thị trong " + RoundResult.DISPLAY_SECONDS + " giây; sau đó bạn chọn sẵn sàng tiếp.");
                }
                default -> {}
            }
            return;
        }

        if (pendingRequest == null || !pendingRequest.id().equals(requestId)) {
            return;
        }

        PendingRequest activeRequest = pendingRequest;

        if (type == PacketType.ERROR) {
            completePendingRequest();
            var errorPayload = (Packet.ErrorPayload) data;
            afterRegisterLogin = null;
            if (pendingNewPassword != null) {
                Arrays.fill(pendingNewPassword, '\0');
                pendingNewPassword = null;
            }

            if (reconnectState.active() && activeRequest.type() == PacketType.LOGIN) {
                handleReconnectFailure(errorPayload.code(), errorPayload.message() == null
                        ? "Không thể khôi phục phiên đăng nhập." : errorPayload.message());
                return;
            }

            if (errorPayload.code() == ErrorCode.UNAUTHENTICATED) {
                resetSignedOutState();
            }
            if ((errorPayload.code() == ErrorCode.NOT_IN_ROOM || errorPayload.code() == ErrorCode.ROOM_NOT_FOUND)
                    && currentRoom != null) {
                currentRoom = null;
                savedRoomId = 0;
                view.showLobby();
                updateUiOperationState();
            }
            if (activeRequest.type() == PacketType.JOIN_ROOM && savedRoomId > 0 && currentRoom == null) {
                savedRoomId = 0;
                view.showLobby();
                loadRoomList(false);
                updateUiOperationState();
                view.showMessage("Không thể vào lại bàn cũ: " + (errorPayload.message() != null ? errorPayload.message() : "Bàn không khả dụng") + ". Đã quay về sảnh.");
                return;
            }
            if (dataDialog != null
                    && dataDialog.isDisplayable()
                    && (activeRequest.type() == PacketType.GET_HISTORY || activeRequest.type() == PacketType.GET_LEADERBOARD)) {
                dataDialog.failure(errorPayload.message());
            }

            view.showMessage(errorPayload.message() == null ? "Không thể thực hiện thao tác này." : errorPayload.message());
            return;
        }

        if (type == PacketType.AUTH_RESULT
                && (activeRequest.type() == PacketType.LOGIN || activeRequest.type() == PacketType.REGISTER)) {
            var authResult = (AuthResult) data;
            completePendingRequest();

            if (!authResult.success()) {
                afterRegisterLogin = null;
                if (reconnectState.active()) {
                    handleReconnectFailure(authResult.errorCode(), authResult.message() == null
                            ? "Không thể khôi phục phiên đăng nhập." : authResult.message());
                    return;
                }
                view.showMessage(authResult.message());
                return;
            }

            if (activeRequest.type() == PacketType.REGISTER) {
                AuthRequest loginPayload = afterRegisterLogin;
                afterRegisterLogin = null;
                view.registrationCompleted();
                view.showMessage("Đăng ký thành công. Đang vào sảnh chơi…");
                if (loginPayload != null) {
                    sendRequest(PacketType.LOGIN, loginPayload);
                }
                return;
            }

            if (authResult.userId() == null || authResult.userId() <= 0) {
                handleDisconnect("Không thể xác thực thông tin tài khoản. Vui lòng đăng nhập lại.");
                return;
            }

            if ("ADMIN".equalsIgnoreCase(authResult.role())) {
                handleDisconnect("Tài khoản Quản trị viên (ADMIN) không được phép đăng nhập vào ứng dụng Game Client.\n\nVui lòng khởi động giao diện quản trị riêng biệt: Start-Admin.cmd");
                return;
            }

            currentUser = authResult;
            String displayName = (authResult.displayName() == null) ? authResult.username() : authResult.displayName();
            view.showSignedIn(displayName, authResult.userId());
            view.setBalance(authResult.balance());

            if (reconnectState.active()) {
                reconnectState.finish();
                reconnectTimer.stop();
                // Reset the old room/version before applying the server's authoritative resync.
                currentRoom = null;
                view.room().game().clear();
                if (savedRoomId > 0) {
                    view.showMessage("Đang khôi phục lại bàn chơi #" + savedRoomId + "…");
                    sendRequest(PacketType.JOIN_ROOM, new JoinRoomRequest(savedRoomId));
                } else {
                    view.showLobby();
                    loadRoomList(false);
                    updateUiOperationState();
                    view.showMessage("✅ Đã khôi phục kết nối thành công!");
                }
                return;
            }

            view.showLobby();
            lastProfileRefreshTime = System.nanoTime();
            loadRoomList(false);
            updateUiOperationState();
            view.showMessage("Chào mừng bạn đến với sảnh chơi Ba Cây.");
            return;
        }

        if (activeRequest.type() == PacketType.ROOM_LIST && type == PacketType.ROOM_LIST_RESULT) {
            completePendingRequest();
            updateRoomListInView(data);
            return;
        }

        if ((activeRequest.type() == PacketType.JOIN_ROOM
                || activeRequest.type() == PacketType.SET_READY
                || activeRequest.type() == PacketType.CHANGE_ROOM_BET)
                && type == PacketType.ROOM_UPDATED) {
            var roomSnapshot = (RoomSnapshot) data;
            if (roomSnapshot.roomId() != activeRequest.roomId()) {
                handleDisconnect("Dữ liệu bàn chơi không đồng bộ. Vui lòng đăng nhập lại.");
                return;
            }

            completePendingRequest();
            applyRoomSnapshot(roomSnapshot, activeRequest.type() == PacketType.JOIN_ROOM);

            if (activeRequest.type() == PacketType.JOIN_ROOM) {
                if (savedRoomId > 0 && savedRoomId == roomSnapshot.roomId()) {
                    view.showMessage("✅ Đã khôi phục kết nối và trở lại " + roomSnapshot.roomName() + "!");
                } else {
                    view.showMessage("Đã vào " + roomSnapshot.roomName() + ". Bấm Sẵn sàng để chơi.");
                }
            } else if (activeRequest.type() == PacketType.CHANGE_ROOM_BET) {
                view.showMessage("Đã đổi mức cược cơ bản. Tất cả người chơi cần xác nhận sẵn sàng lại.");
            } else {
                view.showMessage("Đã cập nhật trạng thái sẵn sàng.");
            }
            return;
        }

        if (type == PacketType.PROFILE_RESULT
                && (activeRequest.type() == PacketType.GET_PROFILE || activeRequest.type() == PacketType.UPDATE_PROFILE)) {
            var profileResult = (ProfileResult) data;
            completePendingRequest();

            lastProfileRefreshTime = System.nanoTime();
            view.showSignedIn(profileResult.displayName(), profileResult.userId());
            if (currentRoom == null) {
                view.setBalance(profileResult.balance());
            }

            if (activeRequest.type() == PacketType.GET_PROFILE && !activeRequest.silent()) {
                view.editProfile(profileResult, currentRoom == null, req -> sendRequest(PacketType.UPDATE_PROFILE, req));
            } else if (activeRequest.type() == PacketType.UPDATE_PROFILE) {
                view.showMessage("Đã lưu thông tin hồ sơ.");
            }
            return;
        }

        if ((activeRequest.type() == PacketType.GET_HISTORY && type == PacketType.HISTORY_RESULT)
                || (activeRequest.type() == PacketType.GET_LEADERBOARD && type == PacketType.LEADERBOARD_RESULT)) {
            completePendingRequest();
            if (dataDialog != null && dataDialog.isDisplayable()) {
                dataDialog.showPage((PageResult<?>) data, activeRequest.type() == PacketType.GET_HISTORY);
            }
            return;
        }

        if (type == PacketType.ACK) {
            completePendingRequest();
            switch (activeRequest.type()) {
                case LOGOUT -> {
                    resetSignedOutState();
                    view.showMessage("Đã đăng xuất tài khoản.");
                }
                case LEAVE_ROOM -> {
                    currentRoom = null;
                    savedRoomId = 0;
                    view.showLobby();
                    updateUiOperationState();
                    loadRoomList(false);
                    view.showMessage("Bạn đã quay về sảnh chơi.");
                }
                case CHAT_MESSAGE -> view.room().clearInput();
                case CHANGE_PASSWORD -> {
                    if (pendingNewPassword != null) {
                        if (savedPassword != null) {
                            Arrays.fill(savedPassword, '\0');
                        }
                        savedPassword = pendingNewPassword;
                        pendingNewPassword = null;
                    }
                    view.showMessage("Đã đổi mật khẩu thành công.");
                }
                case START_GAME_REQUEST, GAME_ACTION, PLAY_AGAIN_VOTE -> {}
                default -> handleDisconnect("Phiên bản game chưa tương thích. Vui lòng mở lại bản mới nhất.");
            }
            return;
        }

        handleDisconnect("Không thể đồng bộ trạng thái game. Vui lòng đăng nhập lại.");
    }

    private void updateRoomListInView(Object data) {
        view.lobby().setRooms(((List<?>) data).stream().map(RoomSummary.class::cast).toList());
    }

    private void applyRoomSnapshot(RoomSnapshot nextSnapshot, boolean isEntering) {
        if (currentUser == null || (!isEntering && (currentRoom == null || nextSnapshot.roomId() != currentRoom.roomId()))) {
            return;
        }
        if (currentRoom != null
                && currentRoom.roomId() == nextSnapshot.roomId()
                && nextSnapshot.stateVersion() <= currentRoom.stateVersion()) {
            return;
        }
        if (!isEntering && currentRoom != null && nextSnapshot.players() != null
                && nextSnapshot.players().stream().noneMatch(p -> p.userId() == currentUser.userId())) {
            currentRoom = null;
            savedRoomId = 0;
            view.showLobby();
            updateUiOperationState();
            loadRoomList(false);
            view.showMessage("Bạn đã rời khỏi bàn chơi.");
            return;
        }

        if (nextSnapshot.roomId() < 1
                || nextSnapshot.roomId() > 20
                || nextSnapshot.players() == null
                || nextSnapshot.players().size() > 8
                || nextSnapshot.players().stream().noneMatch(p -> p.userId() == currentUser.userId())
                || nextSnapshot.players().stream().noneMatch(p -> p.userId() == nextSnapshot.ownerUserId())) {
            handleDisconnect("Trạng thái bàn chơi chưa đồng bộ. Vui lòng đăng nhập lại.");
            return;
        }

        boolean returnedToWaiting = currentRoom != null
                && "RESULT".equals(currentRoom.status())
                && "WAITING".equals(nextSnapshot.status());

        currentRoom = nextSnapshot;
        if (currentRoom != null) {
            savedRoomId = currentRoom.roomId();
        }
        view.showRoom(nextSnapshot, currentUser.userId());

        if (returnedToWaiting) {
            view.showMessage("Kết quả đã ẩn. Bạn có thể bấm Sẵn sàng cho ván mới hoặc rời bàn.");
        }

        for (RoomChatEvent chatEvent : pendingRoomChatEvents) {
            if (chatEvent.roomId() == nextSnapshot.roomId()) {
                view.room().appendChat(chatEvent.message());
            }
        }
        pendingRoomChatEvents.clear();
        updateUiOperationState();
    }

    private void completePendingRequest() {
        pendingRequest = null;
        requestTimeoutTimer.stop();
        updateUiOperationState();
    }

    private void updateUiOperationState() {
        view.setOperationEnabled(queuedAuthPacket == null && pendingRequest == null && !reconnectState.active());
    }

    private void resetSignedOutState() {
        currentUser = null;
        currentRoom = null;
        savedRoomId = 0;
        savedUsername = null;
        if (savedPassword != null) {
            Arrays.fill(savedPassword, '\0');
            savedPassword = null;
        }
        if (pendingNewPassword != null) {
            Arrays.fill(pendingNewPassword, '\0');
            pendingNewPassword = null;
        }
        reconnectState.finish();
        reconnectTimer.stop();
        pendingRoomChatEvents.clear();
        afterRegisterLogin = null;

        if (dataDialog != null) {
            dataDialog.dispose();
            dataDialog = null;
        }

        view.showSignedOut();
        updateUiOperationState();
    }

    private void handleDisconnect(String reason) {
        SocketClient oldClient = client;
        client = null;
        queuedAuthPacket = null;
        afterRegisterLogin = null;
        pendingRequest = null;
        requestTimeoutTimer.stop();
        reconnectTimer.stop();
        reconnectState.finish();

        if (oldClient != null) {
            oldClient.disconnect();
        }

        resetSignedOutState();
        view.showMessage(reason);
    }

    private void runOnSwingThread(SocketClient source, Runnable action) {
        SwingUtilities.invokeLater(() -> {
            if (!isClosing && client == source) {
                action.run();
            }
        });
    }
}
