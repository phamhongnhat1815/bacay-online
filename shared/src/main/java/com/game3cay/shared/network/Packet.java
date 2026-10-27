package com.game3cay.shared.network;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Gói tin chuẩn trao đổi giữa Client và Server (Giao thức v2).
 * <p>
 * Mỗi packet bao gồm:
 * <ul>
 *   <li>{@code protocolVersion}: Phiên bản giao thức (hiện tại là 2).</li>
 *   <li>{@code type}: Loại thông điệp {@link PacketType}.</li>
 *   <li>{@code requestId}: Mã định danh duy nhất của yêu cầu (dùng cho Request-Response, có thể null với push).</li>
 *   <li>{@code data}: Đối tượng dữ liệu (payload), phải được đăng ký trong serialization filter.</li>
 * </ul>
 */
public final class Packet implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Phiên bản giao thức mạng hiện tại. */
    public static final int VERSION = 2;

    private final int protocolVersion;
    private final PacketType type;
    private final String requestId;
    private final Object data;

    /**
     * Khởi tạo gói tin đầy đủ thuộc tính.
     *
     * @param type      loại thông điệp
     * @param requestId mã yêu cầu (nullable với server push)
     * @param data      payload đi kèm (nullable nếu không có dữ liệu)
     */
    public Packet(PacketType type, String requestId, Object data) {
        this.protocolVersion = VERSION;
        this.type = Objects.requireNonNull(type, "type không được null");
        this.requestId = requestId;
        this.data = data;
    }

    /**
     * Khởi tạo gói tin yêu cầu từ client với requestId ngẫu nhiên.
     *
     * @param type loại thông điệp
     * @param data dữ liệu yêu cầu
     */
    public Packet(PacketType type, Object data) {
        this(type, UUID.randomUUID().toString(), data);
    }

    /**
     * Tạo gói tin request với requestId tự sinh.
     *
     * @param type loại request
     * @param data dữ liệu gửi kèm
     * @return Packet tương ứng
     */
    public static Packet request(PacketType type, Object data) {
        return new Packet(type, UUID.randomUUID().toString(), data);
    }

    /**
     * Tạo gói tin Server Push (không có requestId yêu cầu phản hồi).
     *
     * @param type loại sự kiện server phát
     * @param data dữ liệu sự kiện
     * @return Packet tương ứng
     */
    public static Packet serverPush(PacketType type, Object data) {
        return new Packet(type, null, data);
    }

    /**
     * Tạo gói tin ACK (xác nhận thành công một requestId).
     *
     * @param requestId mã yêu cầu cần xác nhận
     * @return Packet phản hồi ACK
     */
    public static Packet ack(String requestId) {
        return new Packet(PacketType.ACK, requestId, null);
    }

    /**
     * Tạo gói tin báo lỗi ERROR cho một requestId cụ thể.
     *
     * @param requestId mã yêu cầu xảy ra lỗi
     * @param code      mã lỗi chuẩn {@link ErrorCode}
     * @param message   thông báo mô tả lỗi cho người dùng
     * @return Packet phản hồi ERROR
     */
    public static Packet error(String requestId, ErrorCode code, String message) {
        return new Packet(PacketType.ERROR, requestId, new ErrorPayload(code, message));
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

    /**
     * Ép kiểu an toàn payload dữ liệu sang class chỉ định.
     *
     * @param <T>   kiểu đối tượng mong muốn
     * @param clazz Class của kiểu đối tượng
     * @return đối tượng đã được ép kiểu
     */
    public <T> T getDataAs(Class<T> clazz) {
        return clazz.cast(data);
    }

    /**
     * Payload dữ liệu chuẩn cho gói tin phản hồi lỗi {@link PacketType#ERROR}.
     */
    public record ErrorPayload(ErrorCode code, String message) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "version=" + protocolVersion +
                ", type=" + type +
                ", requestId='" + requestId + '\'' +
                ", data=" + (data != null ? data.getClass().getSimpleName() : "null") +
                '}';
    }
}
