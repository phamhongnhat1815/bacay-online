package com.game3cay.server.room;

import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.network.Packet;

import java.util.List;

/**
 * Giao diện phát sự kiện từ RoomManager tới các phiên kết nối mạng.
 */
public interface RoomEvents {

    /**
     * Thông báo cập nhật trạng thái phòng cho danh sách người chơi trong phòng.
     *
     * @param room       snapshot trạng thái phòng mới nhất
     * @param recipients danh sách ID người nhận
     */
    void roomUpdated(RoomSnapshot room, List<Long> recipients);

    /**
     * Gửi tin nhắn trò chuyện (văn bản hoặc hệ thống) tới danh sách người nhận.
     *
     * @param roomId     ID bàn chơi
     * @param message    tin nhắn
     * @param recipients danh sách ID người nhận
     */
    void chatMessage(long roomId, ChatMessage message, List<Long> recipients);

    /**
     * Gửi một gói tin tùy ý tới danh sách người nhận.
     *
     * @param packet     gói tin
     * @param recipients danh sách ID người nhận
     */
    default void packet(Packet packet, List<Long> recipients) {}

    /**
     * Phát sóng trạng thái 20 bàn chơi ngoài sảnh cho tất cả người chơi trực tuyến.
     *
     * @param rooms danh sách tóm tắt 20 bàn chơi
     */
    default void lobbyUpdated(List<RoomSummary> rooms) {}
}
