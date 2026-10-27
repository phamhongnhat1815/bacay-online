package com.game3cay.server.network;

import com.game3cay.shared.network.Packet;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bảng quản lý các phiên kết nối trực tuyến (Session Registry) của người chơi.
 * <p>
 * Đảm bảo mỗi tài khoản chỉ đăng nhập tối đa trên 1 kết nối cùng lúc,
 * hỗ trợ gửi tin riêng hoặc phát sóng tin nhắn cho danh sách người chơi.
 */
public final class SessionRegistry {

    private final ConcurrentHashMap<Long, ClientHandler> onlineSessions = new ConcurrentHashMap<>();

    /**
     * Gắn phiên kết nối cho tài khoản người dùng nếu chưa có phiên nào trước đó.
     * Nếu kết nối cũ đã bị ngắt/đóng (do rớt mạng), kết nối mới sẽ được thay thế an toàn.
     *
     * @param userId  mã ID người dùng
     * @param handler kết nối client tương ứng
     * @return true nếu gắn thành công, false nếu tài khoản đang online trên kết nối còn sống
     */
    public synchronized boolean bind(long userId, ClientHandler handler) {
        ClientHandler old = onlineSessions.get(userId);
        if (old != null && old != handler && !old.isClosed()) {
            return false;
        }
        onlineSessions.put(userId, handler);
        return true;
    }

    /**
     * Giải phóng phiên kết nối khi đăng xuất hoặc mất mạng.
     *
     * @param userId  mã ID người dùng
     * @param handler kết nối client cần giải phóng
     */
    public void release(long userId, ClientHandler handler) {
        onlineSessions.remove(userId, handler);
    }

    /**
     * Gửi gói tin tới một nhóm người nhận cụ thể theo danh sách ID.
     *
     * @param recipientUserIds danh sách ID người nhận
     * @param packet           gói tin cần gửi
     */
    public void broadcast(List<Long> recipientUserIds, Packet packet) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }
        for (long userId : recipientUserIds) {
            ClientHandler handler = onlineSessions.get(userId);
            if (handler != null) {
                handler.send(packet);
            }
        }
    }

    /**
     * Phát sóng gói tin tới toàn bộ người chơi đang trực tuyến trên máy chủ.
     *
     * @param packet gói tin cần phát sóng
     */
    public void broadcastAll(Packet packet) {
        onlineSessions.values().forEach(handler -> handler.send(packet));
    }

    public int onlineCount() {
        return onlineSessions.size();
    }

    public java.util.Optional<ClientHandler> find(long userId) {
        return java.util.Optional.ofNullable(onlineSessions.get(userId));
    }
}
