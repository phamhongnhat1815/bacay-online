package com.game3cay.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.game3cay.gui.MainFrame;
import com.game3cay.network.PacketListener;
import com.game3cay.network.SocketClient;
import com.game3cay.shared.model.AuthRequest;
import com.game3cay.shared.model.AuthResult;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.RegisterRequest;
import com.game3cay.shared.model.RoomChatEvent;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketType;

public final class ConnectionController implements PacketListener {
    private final MainFrame view;
    private final Timer responseTimeout;
    private final Timer maintenance;

    private final Map<String, Long> echoes = new HashMap<>();
    private final List<RoomChatEvent> enteringChat = new ArrayList<>();

    private SocketClient client;
    private AuthResult user;
    private RoomSnapshot room;

    private record PendingRequest(
            String id,
            PacketType type,
            long roomId
    ) {}

    private PendingRequest pending;
    private long lastRoomList;
    private boolean closing;
    private int testNumber;

    public ConnectionController(MainFrame view) {
        this.view = view;

        responseTimeout = new Timer(10000, e -> disconnect(
                "Hết thời gian chờ phản hồi. "
                + "Kết nối và đăng nhập lại để đồng bộ trạng thái."
        ));
        responseTimeout.setRepeats(false);

        maintenance = new Timer(1000, e -> maintain());
        maintenance.start();

        view.onConnect(this::connect);
        view.onDisconnect(() -> disconnect("Đã ngắt kết nối."));
        view.onSend(this::sendTest);
        view.onSubmit(this::submitAuth);

        view.onLogout(() -> {
            if (user != null) {
                request(PacketType.LOGOUT, null);
            }
        });

        view.lobby().onRefresh(this::loadRooms);
        view.lobby().onCreate(this::createRoom);
        view.lobby().onJoin(this::joinRoom);

        view.room().onLeave(() -> {
            if (room != null) {
                request(PacketType.LEAVE_ROOM, null);
            }
        });

        view.room().onReady(this::toggleReady);
        view.room().onStart(this::startGame);
        view.room().onSend(this::sendChat);

        view.onClose(() -> {
            closing = true;
            maintenance.stop();
            disconnect("Đã đóng client.");
            view.dispose();
        });
    }

    private void connect() {
        if (client != null) {
            return;
        }

        int port;

        try {
            port = Integer.parseInt(view.getPortText());

            if (view.getHost().isBlank() || port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            view.showMessage(
                    "IP không được trống; port phải từ 1 đến 65535."
            );
            return;
        }

        view.setConnectionState(false, true, "Đang kết nối...");
        view.setOperationEnabled(false);

        client = new SocketClient(this);
        client.connect(view.getHost(), port);
    }

    private void submitAuth() {
        if (user != null || pending != null) {
            return;
        }

        char[] password = view.getPassword();
        char[] confirm = view.getConfirmPassword();
        String username = view.getUsername();

        try {
            String raw = new String(password);

            if (username.isBlank()
                    || username.length() > 50
                    || raw.isBlank()
                    || raw.length() > 128) {

                view.showMessage(
                        "Nhập tài khoản tối đa 50 "
                        + "và mật khẩu tối đa 128 ký tự."
                );
                return;
            }

            if (view.isRegisterMode()) {
                if (raw.length() < 8) {
                    view.showMessage(
                            "Mật khẩu đăng ký cần 8–128 ký tự."
                    );
                    return;
                }

                if (!username.matches("[a-zA-Z0-9_]{3,50}")) {
                    view.showMessage(
                            "Tài khoản mới: 3–50 ký tự, "
                            + "chỉ chữ không dấu, số và _."
                    );
                    return;
                }

                if (!Arrays.equals(password, confirm)) {
                    view.showMessage("Hai mật khẩu không khớp.");
                    return;
                }

                if (view.getDisplayName().length() > 100
                        || view.getEmail().length() > 100) {

                    view.showMessage(
                            "Tên hiển thị và email tối đa 100 ký tự."
                    );
                    return;
                }

                if (!view.getEmail().isBlank()
                        && !view.getEmail().matches(
                                "[^\\s@]+@[^\\s@]+\\.[^\\s@]+"
                        )) {

                    view.showMessage("Email không hợp lệ.");
                    return;
                }

                request(
                        PacketType.REGISTER,
                        new RegisterRequest(
                                username,
                                raw,
                                optional(view.getDisplayName()),
                                optional(view.getEmail())
                        )
                );
            } else {
                request(
                        PacketType.LOGIN,
                        new AuthRequest(username, raw)
                );
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private static String optional(String s) {
        return s.isBlank() ? null : s;
    }

    private void loadRooms() {
        if (user != null && room == null && pending == null) {
            lastRoomList = System.nanoTime();
            request(PacketType.ROOM_LIST, null);
        }
    }

    private void createRoom() {
        if (user == null || room != null || pending != null) {
            return;
        }

        try {
            var v = view.lobby();
            BigDecimal points = new BigDecimal(v.pointText());
            int min = v.minPlayers();
            int max = v.maxPlayers();

            if (v.roomName().isBlank()
                    || v.roomName().length() > 100
                    || min < 2
                    || min > max
                    || max > 8
                    || points.signum() <= 0
                    || points.precision() > 15
                    || points.scale() < 0
                    || points.scale() > 2) {

                throw new IllegalArgumentException();
            }

            request(
                    PacketType.CREATE_ROOM,
                    new CreateRoomRequest(
                            v.roomName(), min, max, points
                    )
            );
        } catch (IllegalArgumentException e) {
            view.showMessage(
                    "Tên phòng 1–100 ký tự; "
                    + "2 ≤ tối thiểu ≤ tối đa ≤ 8; "
                    + "điểm cơ bản > 0, tối đa 2 số thập phân. "
                    + "Dùng dấu chấm."
            );
        }
    }

    private void joinRoom() {
        if (user == null || room != null || pending != null) {
            return;
        }

        long id = view.lobby().selectedRoomId();

        if (id <= 0) {
            view.showMessage("Chọn một phòng trong danh sách.");
        } else {
            request(
                    PacketType.JOIN_ROOM,
                    new JoinRoomRequest(id)
            );
        }
    }

    private void toggleReady() {
        if (user == null
                || room == null
                || !"WAITING".equals(room.status())) {

            return;
        }

        var me = room.players().stream()
                .filter(p -> p.userId() == user.userId())
                .findFirst()
                .orElse(null);

        if (me != null && "PLAYING".equals(me.status())) {
            request(PacketType.SET_READY, !me.ready());
        }
    }

    private void startGame() {
        if (user == null || room == null) {
            return;
        }

        if (room.ownerUserId() != user.userId()) {
            showError(
                    ErrorCode.NOT_ROOM_OWNER,
                    "Chỉ chủ phòng được bắt đầu."
            );
            return;
        }

        var active = room.players().stream()
                .filter(p -> "PLAYING".equals(p.status()))
                .toList();

        if (!"WAITING".equals(room.status())) {
            showError(
                    ErrorCode.INVALID_STATE,
                    "Phòng không ở trạng thái chờ."
            );
        } else if (active.size() < room.minPlayers()) {
            showError(
                    ErrorCode.NOT_ENOUGH_PLAYERS,
                    "Chưa đủ người chơi."
            );
        } else if (!active.stream()
                .allMatch(RoomSnapshot.PlayerSlot::ready)) {

            showError(
                    ErrorCode.NOT_ALL_READY,
                    "Có người chưa sẵn sàng."
            );
        } else {
            request(PacketType.START_GAME_REQUEST, null);
        }
    }

    private void sendChat() {
        if (user == null || room == null || pending != null) {
            return;
        }

        String content = view.room().messageText();

        if (content.isBlank() || content.length() > 500) {
            view.showMessage(
                    "Tin nhắn phải có từ 1 đến 500 ký tự."
            );
            return;
        }

        // Server xác định danh tính, phòng và thời gian.
        request(
                PacketType.CHAT_MESSAGE,
                new ChatMessage(null, null, content, "TEXT", 0)
        );
    }

    private void request(PacketType type, Object data) {
        if (pending != null) {
            return;
        }

        if (client == null || !client.isConnected()) {
            view.showMessage("Bạn cần kết nối server trước.");
            return;
        }

        if (type != PacketType.LOGIN
                && type != PacketType.REGISTER
                && user == null) {

            view.showMessage("Bạn cần đăng nhập trước.");
            return;
        }

        Packet packet = Packet.request(type, data);
        enteringChat.clear();

        long requestedRoom = data instanceof JoinRoomRequest join
                ? join.roomId()
                : room == null ? -1 : room.roomId();

        pending = new PendingRequest(
                packet.getRequestId(), type, requestedRoom
        );

        if (!client.send(packet)) {
            pending = null;
            view.showMessage(
                    "Không xếp được yêu cầu gửi: "
                    + "kết nối đóng hoặc hàng đợi đầy."
            );
            return;
        }

        if (type == PacketType.LOGIN || type == PacketType.REGISTER) {
            view.clearPasswords();
        }

        updateEnabled();
        view.showMessage("Đang xử lý yêu cầu...");
        view.appendLog(
                "[Gửi] " + type
                + " | id=" + packet.getRequestId()
        );
        responseTimeout.restart();
    }

    private void sendTest() {
        if (client == null
                || !client.isConnected()
                || echoes.size() >= 32) {

            return;
        }

        Packet packet = Packet.request(
                PacketType.ECHO_REQUEST,
                "T01 #" + ++testNumber
        );

        if (client.send(packet)) {
            echoes.put(
                    packet.getRequestId(),
                    System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(10)
            );

            view.appendLog(
                    "[Gửi thử] id=" + packet.getRequestId()
            );
        } else {
            view.showMessage("Không xếp được yêu cầu thử.");
        }
    }

    private void maintain() {
        long now = System.nanoTime();

        echoes.entrySet().removeIf(e -> {
            if (now < e.getValue()) {
                return false;
            }

            view.appendLog(
                    "[Quá hạn ACK thử] id=" + e.getKey()
            );
            return true;
        });

        if (user != null
                && room == null
                && pending == null
                && now - lastRoomList >= TimeUnit.SECONDS.toNanos(5)) {

            loadRooms();
        }
    }

    @Override
    public void onConnected(SocketClient source) {
        onUi(source, () -> {
            if (!source.isConnected()) {
                return;
            }

            view.setConnectionState(true, false, "Đã kết nối");
            updateEnabled();
            view.showMessage(
                    "Đã kết nối. Bạn có thể đăng nhập hoặc đăng ký."
            );
        });
    }

    @Override
    public void onPacketReceived(SocketClient source, Packet packet) {
        onUi(source, () -> handle(packet));
    }

    private void handle(Packet packet) {
        PacketType type = packet.getType();
        String id = packet.getRequestId();

        // Không log payload tài khoản hoặc mật khẩu.
        view.appendLog("[Nhận] " + type + " | id=" + id);

        if (type == PacketType.PING) {
            view.appendLog(
                    "[Heartbeat] Nhận PING, "
                    + "đã xếp PONG cùng requestId."
            );
            return;
        }

        if (type == PacketType.SERVER_NOTICE) {
            view.appendLog("[Server] " + packet.getData());
            return;
        }

        if (echoes.containsKey(id)
                && (type == PacketType.ACK
                    || type == PacketType.ERROR)) {

            echoes.remove(id);

            if (type == PacketType.ACK) {
                view.appendLog("[ACK thử khớp] id=" + id);
            } else {
                error((Packet.ErrorPayload) packet.getData());
            }

            return;
        }

        if (type == PacketType.ROOM_UPDATED && id == null) {
            applyRoom((RoomSnapshot) packet.getData(), false);
            return;
        }

        if ((type == PacketType.CHAT_MESSAGE
                || type == PacketType.CHAT_SYSTEM)
                && id == null) {

            if (!(packet.getData() instanceof RoomChatEvent event)) {
                disconnect(
                        "Dữ liệu chat không tương thích. "
                        + "Cần cập nhật client và server cùng phiên bản."
                );
                return;
            }

            // Server có thể phát thông báo vào phòng trước phản hồi riêng.
            if (room == null
                    && pending != null
                    && (pending.type() == PacketType.CREATE_ROOM
                        || pending.type() == PacketType.JOIN_ROOM)) {

                if (enteringChat.size() < 16) {
                    enteringChat.add(event);
                }
                return;
            }

            if (room != null
                    && event.roomId() == room.roomId()
                    && (pending == null
                        || (pending.type() != PacketType.LEAVE_ROOM
                            && pending.type() != PacketType.LOGOUT))) {

                view.room().appendChat(event.message());
            }

            return;
        }

        if (pending == null || !pending.id().equals(id)) {
            return;
        }

        PendingRequest request = pending;

        if (type == PacketType.ERROR) {
            enteringChat.clear();
            finishRequest();

            var failure = (Packet.ErrorPayload) packet.getData();

            if (failure.code() == ErrorCode.UNAUTHENTICATED) {
                signedOut();
            } else if ((failure.code() == ErrorCode.NOT_IN_ROOM
                    || failure.code() == ErrorCode.ROOM_NOT_FOUND)
                    && room != null) {

                room = null;
                view.showLobby();
                updateEnabled();
            }

            error(failure);
            return;
        }

        if ((request.type() == PacketType.LOGIN
                || request.type() == PacketType.REGISTER)
                && type == PacketType.AUTH_RESULT) {

            AuthResult result = (AuthResult) packet.getData();
            finishRequest();

            if (!result.success()) {
                showError(result.errorCode(), result.message());
            } else if (request.type() == PacketType.REGISTER) {
                view.registrationCompleted();
                view.showMessage(
                        "Đăng ký thành công. "
                        + "Hãy đăng nhập bằng tài khoản vừa tạo."
                );
            } else if (result.userId() == null
                    || result.userId() <= 0
                    || result.username() == null
                    || result.username().isBlank()) {

                disconnect(
                        "Phản hồi đăng nhập thiếu danh tính hợp lệ."
                );
            } else {
                user = result;

                String name = result.displayName() == null
                        || result.displayName().isBlank()
                        ? result.username()
                        : result.displayName();

                view.showSignedIn(name, result.userId());
                view.showLobby();
                loadRooms();
            }

            return;
        }

        if (request.type() == PacketType.ROOM_LIST
                && type == PacketType.ROOM_LIST_RESULT) {

            List<RoomSummary> values =
                    ((List<?>) packet.getData()).stream()
                            .map(RoomSummary.class::cast)
                            .toList();

            finishRequest();
            view.lobby().setRooms(values);

            view.showMessage(
                    values.isEmpty()
                            ? "Chưa có phòng. Bạn có thể tạo phòng mới."
                            : "Đã tải danh sách phòng."
            );
            return;
        }

        if ((request.type() == PacketType.CREATE_ROOM
                || request.type() == PacketType.JOIN_ROOM
                || request.type() == PacketType.SET_READY)
                && type == PacketType.ROOM_UPDATED) {

            RoomSnapshot snapshot =
                    (RoomSnapshot) packet.getData();

            if ((request.type() == PacketType.JOIN_ROOM
                    && snapshot.roomId() != request.roomId())
                    || (request.type() == PacketType.SET_READY
                        && (room == null
                            || snapshot.roomId() != room.roomId()))) {

                disconnect(
                        "Phản hồi thao tác phòng không khớp mã phòng."
                );
                return;
            }

            finishRequest();

            if (applyRoom(
                    snapshot,
                    request.type() != PacketType.SET_READY
            )) {
                view.showMessage("Đã cập nhật phòng.");
            }

            return;
        }

        if (type == PacketType.ACK) {
            switch (request.type()) {
                case LOGOUT -> {
                    finishRequest();
                    signedOut();
                    view.showMessage(
                            "Đã đăng xuất. Kết nối vẫn được giữ."
                    );
                }

                case LEAVE_ROOM -> {
                    finishRequest();
                    room = null;
                    view.showLobby();
                    loadRooms();
                }

                case CHAT_MESSAGE -> {
                    finishRequest();
                    view.room().clearInput();
                    view.showMessage("Đã gửi tin nhắn.");
                }

                default -> disconnect(
                        "Server trả ACK không đúng hợp đồng cho "
                        + request.type()
                );
            }

            return;
        }

        disconnect(
                "Server trả sai loại phản hồi cho " + request.type()
        );
    }

    private boolean applyRoom(
            RoomSnapshot snapshot,
            boolean entering
    ) {
        if (user == null) {
            return false;
        }

        if (!entering
                && (room == null
                    || room.roomId() != snapshot.roomId())) {

            return false;
        }

        if (room != null
                && room.roomId() == snapshot.roomId()
                && snapshot.stateVersion() <= room.stateVersion()) {

            return true;
        }

        if (!validRoom(snapshot)) {
            disconnect(
                    "Snapshot phòng thiếu hoặc sai thông tin thành viên."
            );
            return false;
        }

        room = snapshot;
        view.showRoom(snapshot, user.userId());

        for (RoomChatEvent event : enteringChat) {
            if (event.roomId() == snapshot.roomId()) {
                view.room().appendChat(event.message());
            }
        }

        enteringChat.clear();
        updateEnabled();
        return true;
    }

    private boolean validRoom(RoomSnapshot r) {
        if (r.roomId() <= 0
                || r.stateVersion() < 0
                || r.roomName() == null
                || r.roomName().isBlank()
                || r.roomName().length() > 100
                || r.roomCode() == null
                || r.status() == null
                || !Set.of(
                        "WAITING", "PLAYING", "FINISHED"
                ).contains(r.status())
                || r.minPlayers() < 2
                || r.maxPlayers() < r.minPlayers()
                || r.maxPlayers() > 8
                || r.players() == null
                || r.players().size() > r.maxPlayers()) {

            return false;
        }

        Set<Long> ids = new HashSet<>();
        Set<Integer> seats = new HashSet<>();
        boolean mine = false;
        boolean owner = false;

        for (var p : r.players()) {
            if (p == null
                    || p.userId() <= 0
                    || p.seatNumber() < 1
                    || p.seatNumber() > r.maxPlayers()
                    || p.status() == null
                    || !Set.of(
                            "PLAYING", "SPECTATOR", "LEFT"
                    ).contains(p.status())
                    || !ids.add(p.userId())
                    || !seats.add(p.seatNumber())) {

                return false;
            }

            mine |= p.userId() == user.userId()
                    && !"LEFT".equals(p.status());

            owner |= p.userId() == r.ownerUserId()
                    && !"LEFT".equals(p.status());
        }

        return mine && owner;
    }

    private void error(Packet.ErrorPayload e) {
        showError(e.code(), e.message());
    }

    private void showError(ErrorCode code, String message) {
        view.showMessage(
                (code == null ? "Lỗi" : code.name())
                + ": "
                + (message == null || message.isBlank()
                    ? "Yêu cầu thất bại." : message)
        );
    }

    private void finishRequest() {
        responseTimeout.stop();
        pending = null;
        updateEnabled();
    }

    private void updateEnabled() {
        view.setOperationEnabled(
                client != null
                && client.isConnected()
                && pending == null
        );
    }

    private void signedOut() {
        user = null;
        room = null;
        enteringChat.clear();

        view.showSignedOut();
        updateEnabled();
    }

    private void disconnect(String reason) {
        SocketClient old = client;
        client = null;

        if (old != null) {
            old.disconnect();
        }

        echoes.clear();
        finishRequest();
        signedOut();

        view.setConnectionState(false, false, "Chưa kết nối");
        view.showMessage(reason);
        view.appendLog(reason);
    }

    @Override
    public void onDisconnected(SocketClient source, String reason) {
        onUi(source, () -> disconnect(reason));
    }

    private void onUi(SocketClient source, Runnable action) {
        SwingUtilities.invokeLater(() -> {
            // Callback kết nối cũ không được sửa trạng thái kết nối mới.
            if (!closing && client == source) {
                action.run();
            }
        });
    }
}