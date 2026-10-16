package com.game3cay.shared.network;

import java.io.Serializable;
import java.util.UUID;

public final class Packet implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int VERSION = 1;

    private final int protocolVersion;
    private final PacketType type;
    private final String requestId;
    private final Object data;

    public Packet(PacketType type, String requestId, Object data) {
        this.protocolVersion = VERSION;
        this.type = type;
        this.requestId = requestId;
        this.data = data;
    }

    public static Packet request(PacketType type, Object data) {
        return new Packet(type, UUID.randomUUID().toString(), data);
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public PacketType getType() {
        return type;
    }

    public String getRequestId() {
        return requestId;
    }

    public Object getData() {
        return data;
    }
    public record ErrorPayload(ErrorCode code, String message)
            implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }
    public Packet(PacketType type, Object data) {
        this(type, UUID.randomUUID().toString(), data);
    }

    public static Packet serverPush(PacketType type, Object data) {
        return new Packet(type, null, data);
    }

    public static Packet ack(String requestId) {
        return new Packet(PacketType.ACK, requestId, null);
    }

    public static Packet error(
            String requestId,
            ErrorCode code,
            String message
    ) {
        return new Packet(
                PacketType.ERROR,
                requestId,
                new ErrorPayload(code, message)
        );
    }

    public <T> T getDataAs(Class<T> clazz) {
        return clazz.cast(data);
    }

    @Override
    public String toString() {
        return "Packet{type=" + type + ", requestId=" + requestId + "}";
    }
}