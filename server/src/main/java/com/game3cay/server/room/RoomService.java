package com.game3cay.server.room;

import com.game3cay.server.bus.BUSException;
import com.game3cay.server.bus.UserDTO;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;

import java.util.List;

/**
 * Hợp đồng dịch vụ quản lý phòng và bàn chơi Ba Cây.
 */
public interface RoomService {

    /**
     * Lấy danh sách tóm tắt toàn bộ 20 bàn chơi ngoài sảnh.
     *
     * @return danh sách tóm tắt bàn chơi
     * @throws BUSException nếu lỗi nghiệp vụ
     */
    List<RoomSummary> listRooms() throws BUSException;

    /**
     * Yêu cầu tạo phòng mới (hệ thống cố định 20 bàn, yêu cầu này hướng dẫn chọn bàn sẵn có).
     */
    RoomSnapshot create(UserDTO actor, CreateRoomRequest request) throws BUSException;

    /**
     * Tham gia vào một bàn chơi.
     *
     * @param actor   thông tin người chơi tham gia
     * @param request thông tin bàn yêu cầu vào
     * @return snapshot trạng thái bàn sau khi vào
     * @throws BUSException nếu bàn đầy, đang chơi, hoặc người chơi đang ở bàn khác
     */
    RoomSnapshot join(UserDTO actor, JoinRoomRequest request) throws BUSException;

    /**
     * Rời khỏi bàn chơi hiện tại.
     *
     * @param userId mã người chơi rời bàn
     * @throws BUSException nếu người chơi chưa ở trong bàn nào
     */
    void leave(long userId) throws BUSException;

    /**
     * Bật hoặc tắt trạng thái sẵn sàng tham gia ván mới.
     *
     * @param userId mã người chơi
     * @param ready  trạng thái sẵn sàng mới
     * @return snapshot trạng thái phòng sau khi cập nhật
     * @throws BUSException nếu không đủ số dư hoặc bàn đang diễn ra ván bài
     */
    RoomSnapshot setReady(long userId, boolean ready) throws BUSException;

    /**
     * Gửi tin nhắn trò chuyện vào phòng hiện tại.
     *
     * @param userId  mã người chơi gửi tin
     * @param content nội dung tin nhắn
     * @throws BUSException nếu tin nhắn quá dài hoặc vượt tần suất gửi cho phép
     */
    void chat(long userId, String content) throws BUSException;

    /**
     * Kiểm tra điều kiện có thể bắt đầu ván bài (chủ bàn, đủ người, tất cả đã sẵn sàng).
     *
     * @param userId mã người yêu cầu bắt đầu
     * @throws BUSException nếu không thỏa mãn điều kiện bắt đầu
     */
    void checkStart(long userId) throws BUSException;

    /**
     * Xử lý dọn dẹp khi người chơi bị mất kết nối hoặc ngắt socket dứt điểm.
     *
     * @param userId mã người chơi mất kết nối
     */
    void onDisconnected(long userId);

    /**
     * Xử lý khi người chơi tạm thời mất kết nối (giật mạng / Wi-Fi chập chờn).
     * Bật thời gian chờ grace period để người chơi có cơ hội tự động kết nối lại vào đúng bàn.
     *
     * @param userId mã người chơi mất kết nối
     */
    void onConnectionLost(long userId);
}
