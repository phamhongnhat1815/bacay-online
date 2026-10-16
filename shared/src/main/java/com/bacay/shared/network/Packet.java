package com.bacay.shared.network;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Đơn vị thông điệp duy nhất truyền giữa client và server.
 *
 * <p>Quy tắc sử dụng:
 * <ul>
 *   <li>Mỗi request từ client đính kèm {@code requestId} ngẫu nhiên để đối chiếu phản hồi.</li>
 *   <li>Server phản hồi giữ nguyên {@code requestId} của request tương ứng.</li>
 *   <li>Thông báo chủ động từ server (broadcast, push) dùng {@code requestId = null}.</li>
 *   <li>{@code data} phải là {@link Serializable} và khớp kiểu payload của {@code type} đó.</li>
 *   <li>Không đặt socket, stream, JDBC connection hoặc đối tượng quản lý luồng vào {@code data}.</li>
 * </ul>
 */
public final class Packet implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Phiên bản giao thức — phải khớp giữa client và server. */
    public static final String PROTOCOL_VERSION = "1.0";

    private final String protocolVersion;
    private final PacketType type;
    /**
     * ID để đối chiếu request ↔ response.
     * Request client tự sinh (UUID); server copy lại khi phản hồi.
     * Thông báo chủ động từ server để {@code null}.
     */
    private final String requestId;
    /**
     * Payload tương ứng với từng {@link PacketType}.
     * Xem {@code docs/Protocol.md} để biết kiểu cụ thể của từng type.
     */
    private final Object data;

    // --- Constructors ---

    /** Tạo Packet cho request từ client (tự sinh requestId). */
    public Packet(PacketType type, Object data) {
        this(type, UUID.randomUUID().toString(), data);
    }

    /** Tạo Packet với requestId cho trước (dùng khi server phản hồi hoặc test). */
    public Packet(PacketType type, String requestId, Object data) {
        this.protocolVersion = PROTOCOL_VERSION;
        this.type = type;
        this.requestId = requestId;
        this.data = data;
    }

    /** Tạo thông báo chủ động từ server (broadcast, push — không có requestId). */
    public static Packet serverPush(PacketType type, Object data) {
        return new Packet(type, null, data);
    }

    /** Tạo phản hồi thành công cho một request (giữ requestId gốc). */
    public static Packet ack(String requestId) {
        return new Packet(PacketType.ACK, requestId, null);
    }

    /** Tạo phản hồi lỗi cho một request (giữ requestId gốc). */
    public static Packet error(String requestId, ErrorCode code, String message) {
        return new Packet(PacketType.ERROR, requestId, new ErrorPayload(code, message));
    }

    // --- Getters ---

    public String getProtocolVersion() { return protocolVersion; }
    public PacketType getType()        { return type; }
    public String getRequestId()       { return requestId; }
    public Object getData()            { return data; }

    /** Ép kiểu data theo kiểu mong đợi — ném ClassCastException nếu sai. */
    @SuppressWarnings("unchecked")
    public <T> T getDataAs(Class<T> clazz) {
        return clazz.cast(data);
    }

    @Override
    public String toString() {
        return "Packet{type=" + type + ", requestId=" + requestId + "}";
    }

    // -------------------------------------------------------------------------
    // Inner payload classes dùng chung cho ACK / ERROR
    // -------------------------------------------------------------------------

    /** Payload kèm theo Packet ERROR. */
    public record ErrorPayload(ErrorCode code, String message) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
