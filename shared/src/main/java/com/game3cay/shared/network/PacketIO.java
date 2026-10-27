package com.game3cay.shared.network;

import com.game3cay.shared.model.*;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.util.List;

/**
 * Tiện ích I/O cho giao thức mạng {@link Packet}.
 * <p>
 * Đảm nhận:
 * <ul>
 *   <li>Thiết lập {@link ObjectInputFilter} bảo mật chống deserialization exploit.</li>
 *   <li>Đọc và ghi gói tin có kiểm tra hợp lệ cấu trúc và payload.</li>
 *   <li>Đồng bộ hóa việc ghi trên {@link ObjectOutputStream}.</li>
 * </ul>
 */
public final class PacketIO {

    private static final String DESERIALIZATION_FILTER_PATTERN =
            "maxdepth=24;maxarray=4096;"
                    + "com.game3cay.shared.model.*;"
                    + "com.game3cay.shared.network.Packet;"
                    + "com.game3cay.shared.network.Packet$ErrorPayload;"
                    + "com.game3cay.shared.network.PacketType;"
                    + "com.game3cay.shared.network.ErrorCode;"
                    + "java.lang.Enum;java.lang.String;java.lang.Number;"
                    + "java.lang.Long;java.lang.Boolean;java.lang.Object;"
                    + "java.math.BigDecimal;java.math.BigInteger;"
                    + "java.util.ArrayList;java.util.CollSer;"
                    + "java.util.ImmutableCollections$List12;"
                    + "java.util.ImmutableCollections$ListN;!*";

    private PacketIO() {}

    /**
     * Mở luồng ObjectInputStream từ Socket và cài đặt bộ lọc bảo mật deserialization.
     *
     * @param socket kết nối Socket đang mở
     * @return ObjectInputStream đã được lọc an toàn
     * @throws IOException nếu lỗi khởi tạo luồng
     */
    public static ObjectInputStream openInput(Socket socket) throws IOException {
        var input = new ObjectInputStream(socket.getInputStream());
        input.setObjectInputFilter(ObjectInputFilter.Config.createFilter(DESERIALIZATION_FILTER_PATTERN));
        return input;
    }

    /**
     * Đọc một {@link Packet} từ luồng nhập và kiểm tra tính hợp lệ dữ liệu.
     *
     * @param input luồng ObjectInputStream
     * @return Packet hợp lệ
     * @throws IOException            nếu dữ liệu sai định dạng hoặc lỗi socket
     * @throws ClassNotFoundException  nếu lớp không tìm thấy trong classpath
     */
    public static Packet read(ObjectInputStream input) throws IOException, ClassNotFoundException {
        Object raw = input.readObject();
        if (!(raw instanceof Packet packet)) {
            throw new IOException("Dữ liệu nhận được không phải là Packet hợp lệ");
        }
        validate(packet);
        return packet;
    }

    /**
     * Ghi một {@link Packet} ra luồng xuất, đảm bảo đồng bộ hóa luồng ghi và reset cache.
     *
     * @param output luồng ObjectOutputStream
     * @param packet gói tin cần gửi
     * @throws IOException nếu lỗi ghi mạng
     */
    public static void write(ObjectOutputStream output, Packet packet) throws IOException {
        validate(packet);
        synchronized (output) {
            output.reset();
            output.writeObject(packet);
            output.flush();
        }
    }

    // --- Validation Helpers ---

    private static boolean isNonEmptyText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private static boolean isOptionalText(String value, int maxLength) {
        return value == null || value.length() <= maxLength;
    }

    private static boolean isValidAmount(BigDecimal amount) {
        return amount != null
                && amount.signum() > 0
                && amount.precision() <= 15
                && amount.scale() >= 0
                && amount.scale() <= 2;
    }

    /**
     * Xác thực cấu trúc cơ bản và payload của Packet.
     *
     * @param packet gói tin cần xác thực
     * @throws IOException nếu gói tin không hợp lệ
     */
    public static void validate(Packet packet) throws IOException {
        if (packet == null) {
            throw new IOException("Packet không được null");
        }
        if (packet.getType() == null) {
            throw new IOException("Loại Packet (PacketType) không được null");
        }
        if (packet.getProtocolVersion() != Packet.VERSION) {
            throw new IOException("Sai phiên bản giao thức: yêu cầu v" + Packet.VERSION
                    + " nhưng nhận được v" + packet.getProtocolVersion());
        }
        if (packet.getRequestId() != null && !isNonEmptyText(packet.getRequestId(), 64)) {
            throw new IOException("RequestId không hợp lệ (tối đa 64 ký tự)");
        }

        Object data = packet.getData();
        boolean valid = switch (packet.getType()) {
            case PING, PONG -> data == null || (data instanceof String s && s.length() <= 1000);
            case ECHO_REQUEST, SERVER_NOTICE -> data instanceof String s && isNonEmptyText(s, 1000);
            case ACK, LOGOUT, ROOM_LIST, START_GAME_REQUEST, GET_PROFILE -> data == null;
            case LEAVE_ROOM -> data == null || (data instanceof String s && isNonEmptyText(s, 500));

            case LOGIN -> data instanceof AuthRequest a
                    && isNonEmptyText(a.username(), 50)
                    && isNonEmptyText(a.password(), 128);

            case REGISTER -> data instanceof RegisterRequest a
                    && isNonEmptyText(a.username(), 50)
                    && isNonEmptyText(a.password(), 128)
                    && isOptionalText(a.displayName(), 100)
                    && isOptionalText(a.email(), 100);

            case AUTH_RESULT -> data instanceof AuthResult;
            case ERROR -> data instanceof Packet.ErrorPayload e
                    && e.code() != null
                    && isNonEmptyText(e.message(), 500);

            case CREATE_ROOM -> data instanceof CreateRoomRequest r
                    && isNonEmptyText(r.roomName(), 100)
                    && r.minPlayers() >= 2
                    && r.maxPlayers() >= r.minPlayers()
                    && r.maxPlayers() <= 8
                    && isValidAmount(r.betAmount());

            case JOIN_ROOM -> data instanceof JoinRoomRequest r && r.roomId() > 0;
            case SET_READY -> data instanceof Boolean;
            case CHANGE_ROOM_BET -> data instanceof BigDecimal amount && isValidAmount(amount);

            case ROOM_UPDATED -> data instanceof RoomSnapshot r
                    && r.players() != null
                    && r.players().size() <= 8;

            case ROOM_LIST_RESULT -> data instanceof List<?> list
                    && list.size() <= 100
                    && list.stream().allMatch(RoomSummary.class::isInstance);

            case CHAT_MESSAGE -> (data instanceof ChatMessage c
                    && c.content() != null
                    && c.content().length() <= 2000
                    && ("TEXT".equals(c.type()) || "EMOJI".equals(c.type()) || "SYSTEM".equals(c.type())))
                    || (data instanceof RoomChatEvent e
                    && isValidChatEvent(e)
                    && "TEXT".equals(e.message().type()));

            case CHAT_SYSTEM -> data instanceof RoomChatEvent e
                    && isValidChatEvent(e)
                    && "SYSTEM".equals(e.message().type());

            case START_GAME -> data instanceof GameStartInfo g
                    && g.roomId() > 0
                    && isNonEmptyText(g.roundId(), 64);

            case DEAL_CARD -> data instanceof DealCardPayload d
                    && isNonEmptyText(d.roundId(), 64)
                    && d.cards() != null
                    && d.cards().size() == 3;

            case GAME_ACTION -> data instanceof GameActionRequest g
                    && isNonEmptyText(g.roundId(), 64)
                    && g.action() != null;

            case GAME_UPDATED -> data instanceof GameStateSnapshot g
                    && isNonEmptyText(g.roundId(), 64)
                    && g.activePlayers() != null
                    && g.activePlayers().size() <= 8;

            case PLAYER_STATE -> data instanceof PlayerStateSnapshot p
                    && isNonEmptyText(p.roundId(), 64)
                    && p.myCards() != null
                    && p.myCards().size() == 3;

            case BET_UPDATED -> data instanceof BetStateSnapshot b
                    && b.playerBets() != null
                    && b.playerBets().size() <= 8;

            case ROUND_RESULT -> data instanceof RoundResult r
                    && r.playerResults() != null
                    && r.playerResults().size() <= 8
                    && r.betHistory() != null
                    && r.betHistory().size() <= 1024;

            case PLAY_AGAIN_PROMPT -> data instanceof PlayAgainPrompt;
            case PLAY_AGAIN_VOTE -> data instanceof Boolean;

            case GET_HISTORY, GET_LEADERBOARD -> data instanceof PageRequest;

            case HISTORY_RESULT -> data instanceof PageResult<?> p
                    && p.items() != null
                    && p.items().size() <= 100
                    && p.items().stream().allMatch(GameHistoryItem.class::isInstance);

            case LEADERBOARD_RESULT -> data instanceof PageResult<?> p
                    && p.items() != null
                    && p.items().size() <= 100
                    && p.items().stream().allMatch(LeaderboardItem.class::isInstance);

            case PROFILE_RESULT -> data instanceof ProfileResult;

            case UPDATE_PROFILE -> data instanceof UpdateProfileRequest p
                    && isOptionalText(p.displayName(), 100)
                    && isOptionalText(p.email(), 100)
                    && isOptionalText(p.avatarUrl(), 255);

            case CHANGE_PASSWORD -> data instanceof ChangePasswordRequest p
                    && isNonEmptyText(p.currentPassword(), 128)
                    && isNonEmptyText(p.newPassword(), 128);

            case MUTE_PLAYER -> data instanceof Long uid && uid > 0;

            case ADMIN_STATS_REQ -> data == null;
            case ADMIN_STATS_RESP -> data instanceof AdminStatsSnapshot;

            case ADMIN_USERS_PAGE_REQ -> data instanceof AdminUserFilterRequest;
            case ADMIN_USERS_PAGE_RESP -> data instanceof PageResult<?> p
                    && p.items() != null
                    && p.items().stream().allMatch(AdminUserItem.class::isInstance);

            case ADMIN_LOCK_USER_REQ -> data instanceof AdminLockUserRequest;
            case ADMIN_ADJUST_BALANCE_REQ -> data instanceof AdminAdjustBalanceRequest r
                    && r.delta() != null && r.delta().precision() <= 15 && r.delta().scale() <= 2;

            case ADMIN_ROOMS_MONITOR_REQ -> data == null;
            case ADMIN_ROOMS_MONITOR_RESP -> data instanceof List<?> list
                    && list.stream().allMatch(AdminRoomDetail.class::isInstance);

            case ADMIN_FORCE_RESET_ROOM -> data instanceof AdminResetRoomRequest r
                    && r.roomId() > 0;

            case ADMIN_INSPECT_ROOM_REQ -> data instanceof Long roomId && roomId > 0;
            case ADMIN_INSPECT_ROOM_RESP -> data instanceof AdminLiveMatchDetail d && d.roomId() > 0;

            case ADMIN_BROADCAST_REQ -> data instanceof AdminBroadcastRequest r
                    && isNonEmptyText(r.message(), 500);

            case ADMIN_LEDGER_PAGE_REQ -> data instanceof PageRequest;
            case ADMIN_LEDGER_PAGE_RESP -> data instanceof PageResult<?> p
                    && p.items() != null
                    && p.items().stream().allMatch(AdminLedgerItem.class::isInstance);
        };

        if (!valid) {
            throw new IOException("Payload không hợp lệ cho loại thông điệp: " + packet.getType());
        }
    }

    private static boolean isValidChatEvent(RoomChatEvent event) {
        if (event == null || event.roomId() < 0) {
            return false;
        }
        ChatMessage c = event.message();
        if (c == null || !isNonEmptyText(c.content(), 500) || c.timestampMillis() <= 0) {
            return false;
        }
        if ("SYSTEM".equals(c.type())) {
            return true;
        }
        return "TEXT".equals(c.type())
                && c.senderId() != null
                && c.senderId() > 0
                && isNonEmptyText(c.senderDisplayName(), 100);
    }
}
