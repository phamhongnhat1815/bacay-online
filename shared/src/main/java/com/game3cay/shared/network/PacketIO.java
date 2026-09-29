package com.game3cay.shared.network;

import com.game3cay.shared.model.*;
import java.io.*;
import java.net.Socket;
import java.util.List;

public final class PacketIO {
    private PacketIO() {}

    public static ObjectInputStream openInput(Socket socket) throws IOException {
        var input = new ObjectInputStream(socket.getInputStream());

        input.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                "maxdepth=16;maxarray=256;"
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
                + "java.util.ImmutableCollections$ListN;!*"
        ));

        return input;
    }

    public static Packet read(ObjectInputStream input)
            throws IOException, ClassNotFoundException {
        if (!(input.readObject() instanceof Packet packet)) {
            throw new IOException("Dữ liệu không phải Packet");
        }

        validate(packet);
        return packet;
    }

    public static void write(ObjectOutputStream output, Packet packet)
            throws IOException {
        validate(packet);

        synchronized (output) {
            output.reset();
            output.writeObject(packet);
            output.flush();
        }
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    private static boolean optional(String value, int max) {
        return value == null || value.length() <= max;
    }

    private static void validate(Packet packet) throws IOException {
        if (packet == null
                || packet.getType() == null
                || packet.getProtocolVersion() != Packet.VERSION
                || (packet.getRequestId() != null
                    && !text(packet.getRequestId(), 64))) {
            throw new IOException("Packet sai định dạng hoặc phiên bản");
        }

        Object data = packet.getData();

        boolean valid = switch (packet.getType()) {
            case PING, PONG ->
                data == null
                || data instanceof String s && s.length() <= 1000;

            case ECHO_REQUEST, SERVER_NOTICE ->
                data instanceof String s && text(s, 1000);

            case ACK, LOGOUT, ROOM_LIST, LEAVE_ROOM, START_GAME_REQUEST ->
                data == null;

            case LOGIN ->
                data instanceof AuthRequest a
                && text(a.username(), 50)
                && text(a.password(), 128);

            case REGISTER ->
                data instanceof RegisterRequest a
                && text(a.username(), 50)
                && text(a.password(), 128)
                && optional(a.displayName(), 100)
                && optional(a.email(), 100);

            case AUTH_RESULT ->
                data instanceof AuthResult;

            case ERROR ->
                data instanceof Packet.ErrorPayload e
                && e.code() != null && text(e.message(), 500);

            case CREATE_ROOM ->
                data instanceof CreateRoomRequest r
                && text(r.roomName(), 100)
                && r.minPlayers() >= 2
                && r.maxPlayers() >= r.minPlayers()
                && r.maxPlayers() <= 8
                && r.betAmount() != null
                && r.betAmount().signum() > 0
                && r.betAmount().precision() <= 15
                && r.betAmount().scale() >= 0
                && r.betAmount().scale() <= 2;

            case JOIN_ROOM ->
                data instanceof JoinRoomRequest r && r.roomId() > 0;

            case SET_READY ->
                data instanceof Boolean;

            case ROOM_UPDATED ->
                data instanceof RoomSnapshot r
                && r.players() != null && r.players().size() <= 8;

            case ROOM_LIST_RESULT ->
                data instanceof List<?> list
                && list.size() <= 100
                && list.stream().allMatch(RoomSummary.class::isInstance);

            case CHAT_MESSAGE, CHAT_SYSTEM ->
                data instanceof ChatMessage c
                && text(c.content(), 500)
                && ("TEXT".equals(c.type())
                    || "EMOJI".equals(c.type())
                    || "SYSTEM".equals(c.type()));

            default -> false;
        };

        if (!valid) {
            throw new IOException(
                    "Payload không hợp lệ cho " + packet.getType()
            );
        }
    }
}