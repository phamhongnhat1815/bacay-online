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
}