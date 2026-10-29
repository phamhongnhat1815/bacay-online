package com.game3cay.shared.network;

import com.game3cay.shared.model.*;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.util.List;

public final class PacketIO {
    private static final String DESERIALIZATION_FILTER_PATTERN =
            "maxdepth=24;maxarray=4096;maxrefs=20000;maxbytes=1048576;"
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

    public static final int MAGIC = 0x3C33;
    public static final int MAX_FRAME_BYTES = 1_048_576;
    public static final int MAX_CHAT_TEXT = 500;
    public static final int MAX_BROADCAST_TEXT = 500;
    public static final int MAX_SYSTEM_TEXT = 600;
    public static final int FRAME_TIMEOUT_MS = 10_000;

    public static Input openInput(Socket socket) throws IOException {
        return new Input(socket);
    }

    public static Output openOutput(Socket socket) throws IOException {
        return new Output(socket.getOutputStream());
    }

    public static Packet read(Input input) throws IOException, ClassNotFoundException {
        return input.readPacket();
    }

    public static void write(Output output, Packet packet) throws IOException {
        validate(packet);
        output.writePacket(packet);
    }

    /** One bounded object stream per frame; there is no deserialization on the network stream. */
    public static final class Input implements AutoCloseable {
        private final Socket socket;
        private final InputStream raw;
        private int idleTimeoutMillis = 20_000;

        private Input(Socket socket) throws IOException {
            this.socket = socket;
            this.raw = socket.getInputStream();
        }

        private Packet readPacket() throws IOException, ClassNotFoundException {
            socket.setSoTimeout(idleTimeoutMillis);
            int first = raw.read();
            if (first < 0) throw new EOFException("Kết nối đã đóng");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(FRAME_TIMEOUT_MS);
            byte[] header = new byte[8];
            header[0] = (byte) first;
            readFully(header, 1, 7, deadline);
            var frame = new DataInputStream(new ByteArrayInputStream(header));
            if (frame.readUnsignedShort() != MAGIC) throw new IOException("Sai magic của giao thức");
            int version = frame.readUnsignedByte();
            int typeCode = frame.readUnsignedByte();
            int length = frame.readInt();
            if (version != Packet.VERSION) throw new IOException("Phiên bản giao thức không tương thích: " + version);
            if (typeCode < 1 || typeCode > PacketType.values().length) throw new IOException("Mã packet không hợp lệ");
            if (length < 1 || length > MAX_FRAME_BYTES) throw new IOException("Kích thước packet vượt giới hạn");
            byte[] payload = new byte[length];
            readFully(payload, 0, length, deadline);
            try (var objects = new ObjectInputStream(new ByteArrayInputStream(payload))) {
                objects.setObjectInputFilter(ObjectInputFilter.Config.createFilter(DESERIALIZATION_FILTER_PATTERN));
                Object rawPacket = objects.readObject();
                if (!(rawPacket instanceof Packet packet)) throw new IOException("Dữ liệu không phải Packet");
                if (packet.getType() != PacketType.values()[typeCode - 1]) throw new IOException("Loại packet không khớp header");
                validate(packet);
                if (objects.read() != -1) throw new IOException("Dữ liệu thừa trong packet");
                return packet;
            }
        }

        private void readFully(byte[] buffer, int offset, int length, long deadline) throws IOException {
            while (length > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new java.net.SocketTimeoutException("Quá thời gian đọc packet");
                socket.setSoTimeout((int) Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining)));
                int read = raw.read(buffer, offset, length);
                if (read < 0) throw new EOFException("Packet chưa nhận đủ dữ liệu");
                offset += read;
                length -= read;
            }
        }

        public void setIdleTimeoutMillis(int timeout) {
            if (timeout < 1) throw new IllegalArgumentException("Timeout phải dương");
            idleTimeoutMillis = timeout;
        }

        @Override public void close() throws IOException { raw.close(); }
    }

    public static final class Output implements AutoCloseable {
        private final DataOutputStream raw;
        private Output(OutputStream raw) { this.raw = new DataOutputStream(raw); }

        private synchronized void writePacket(Packet packet) throws IOException {
            var bytes = new ByteArrayOutputStream();
            var bounded = new FilterOutputStream(bytes) {
                private int count;
                private void reserve(int amount) throws IOException {
                    if (amount > MAX_FRAME_BYTES - count) throw new IOException("Kích thước packet vượt giới hạn");
                    count += amount;
                }
                @Override public void write(int value) throws IOException { reserve(1); out.write(value); }
                @Override public void write(byte[] b, int off, int len) throws IOException { reserve(len); out.write(b, off, len); }
            };
            try (var objects = new ObjectOutputStream(bounded)) { objects.writeObject(packet); }
            raw.writeShort(MAGIC);
            raw.writeByte(Packet.VERSION);
            raw.writeByte(packet.getType().ordinal() + 1);
            raw.writeInt(bytes.size());
            bytes.writeTo(raw);
            raw.flush();
        }

        public synchronized void flush() throws IOException { raw.flush(); }
        @Override public synchronized void close() throws IOException { raw.close(); }
    }

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
                    && c.content().length() <= MAX_CHAT_TEXT
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
                    && r.userId() > 0 && r.delta() != null && r.delta().signum() != 0
                    && r.delta().precision() <= 15 && r.delta().scale() >= 0 && r.delta().scale() <= 2
                    && isNonEmptyText(r.reason(), 255);

            case ADMIN_ROOMS_MONITOR_REQ -> data == null;
            case ADMIN_ROOMS_MONITOR_RESP -> data instanceof List<?> list
                    && list.stream().allMatch(AdminRoomDetail.class::isInstance);

            case ADMIN_FORCE_RESET_ROOM -> data instanceof AdminResetRoomRequest r
                    && r.roomId() > 0 && isOptionalText(r.reason(), 255);

            case ADMIN_INSPECT_ROOM_REQ -> data instanceof Long roomId && roomId > 0;
            case ADMIN_INSPECT_ROOM_RESP -> data instanceof AdminLiveMatchDetail d && d.roomId() > 0;

            case ADMIN_BROADCAST_REQ -> data instanceof AdminBroadcastRequest r
                    && isNonEmptyText(r.message(), MAX_BROADCAST_TEXT);

            case ADMIN_LEDGER_PAGE_REQ -> data instanceof PageRequest;
            case ADMIN_LEDGER_PAGE_RESP -> data instanceof PageResult<?> p
                    && p.items() != null
                    && p.items().stream().allMatch(AdminLedgerItem.class::isInstance);

            case POINT_TRANSFER -> data instanceof PointTransferRequest r
                    && isUuid(r.operationId()) && r.recipientId() > 0
                    && isValidAmount(r.amount()) && isOptionalText(r.message(), 120);
            case POINT_REQUEST_CREATE -> data instanceof PointRequestCreate r
                    && isUuid(r.operationId()) && r.targetUserId() > 0
                    && isValidAmount(r.amount()) && isOptionalText(r.message(), 120);
            case POINT_REQUEST_REPLY -> data instanceof PointRequestReply r
                    && isUuid(r.operationId()) && isUuid(r.requestId()) && r.decision() != null;
            case POINT_REQUEST_LIST, POINT_TRANSFER_HISTORY -> data instanceof PageRequest p
                    && p.page() >= 0 && p.page() <= 1_000_000 && p.pageSize() >= 1 && p.pageSize() <= 100;
            case POINT_TRANSFER_RESULT -> data instanceof PointTransferReceipt r && validTransfer(r);
            case POINT_REQUEST_UPDATED -> data instanceof PointRequestItem r && validPointRequest(r);
            case POINT_REQUEST_LIST_RESULT -> data instanceof PageResult<?> p
                    && p.items() != null && p.items().size() <= 100
                    && p.items().stream().allMatch(item -> item instanceof PointRequestItem r && validPointRequest(r));
            case POINT_TRANSFER_HISTORY_RESULT -> data instanceof PageResult<?> p
                    && p.items() != null && p.items().size() <= 100
                    && p.items().stream().allMatch(item -> item instanceof PointTransferReceipt r && validTransfer(r));
            case BALANCE_UPDATED -> data instanceof PointBalanceUpdate b && b.userId() > 0
                    && isUuid(b.transferId()) && b.balance() != null && b.balance().signum() >= 0
                    && b.balance().precision() <= 15 && b.balance().scale() >= 0 && b.balance().scale() <= 2;
        };

        if (!valid) {
            throw new IOException("Payload không hợp lệ cho loại thông điệp: " + packet.getType());
        }
    }

    private static boolean isUuid(String id) {
        if (id == null || id.length() != 36) return false;
        try { return java.util.UUID.fromString(id).toString().equalsIgnoreCase(id); }
        catch (IllegalArgumentException e) { return false; }
    }

    private static boolean validTransfer(PointTransferReceipt r) {
        return isUuid(r.transferId()) && isUuid(r.operationId())
                && (r.requestId() == null || isUuid(r.requestId()))
                && r.senderId() > 0 && r.recipientId() > 0 && r.senderId() != r.recipientId()
                && isNonEmptyText(r.senderName(), 100) && isNonEmptyText(r.recipientName(), 100)
                && isValidAmount(r.amount()) && isOptionalText(r.message(), 120) && r.committedAtMillis() > 0;
    }

    private static boolean validPointRequest(PointRequestItem r) {
        return isUuid(r.requestId()) && r.requesterId() > 0 && r.targetUserId() > 0
                && r.requesterId() != r.targetUserId() && r.roomId() > 0
                && isNonEmptyText(r.requesterName(), 100) && isNonEmptyText(r.targetName(), 100)
                && isValidAmount(r.amount()) && isOptionalText(r.message(), 120) && r.status() != null
                && r.createdAtMillis() > 0 && r.expiresAtMillis() > r.createdAtMillis()
                && (r.status() == PointRequestStatus.FULFILLED ? isUuid(r.transferId()) : r.transferId() == null);
    }

    private static boolean isValidChatEvent(RoomChatEvent event) {
        if (event == null || event.roomId() < 0) {
            return false;
        }
        ChatMessage c = event.message();
        if (c == null || !isNonEmptyText(c.content(), "SYSTEM".equals(c.type()) ? MAX_SYSTEM_TEXT : MAX_CHAT_TEXT) || c.timestampMillis() <= 0) {
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
