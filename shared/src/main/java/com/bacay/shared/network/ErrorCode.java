package com.bacay.shared.network;

/**
 * Mã lỗi ổn định dùng trong {@link Packet.ErrorPayload}.
 *
 * <p>UI client chỉ được kiểm tra {@code ErrorCode}, KHÔNG so sánh chuỗi message.
 * Message chỉ dùng để hiển thị hoặc log, không phải để phân nhánh logic.
 */
public enum ErrorCode {

    // Lỗi chung
    INVALID_REQUEST,    // Request sai định dạng / thiếu trường
    UNAUTHENTICATED,    // Chưa đăng nhập mà dùng chức năng cần xác thực
    FORBIDDEN,          // Đã đăng nhập nhưng không có quyền
    INTERNAL_ERROR,     // Lỗi phía server không phải do client

    // Tài khoản
    USERNAME_EXISTS,    // Đăng ký trùng username
    INVALID_CREDENTIALS,// Sai username hoặc mật khẩu
    ALREADY_LOGGED_IN,  // Tài khoản đang có phiên khác hoạt động
    USER_BLOCKED,       // Tài khoản bị khóa

    // Phòng
    ROOM_NOT_FOUND,     // roomId không tồn tại hoặc đã đóng
    ROOM_FULL,          // Phòng đã đủ người
    ROOM_NOT_WAITING,   // Phòng đang chơi, không thể vào
    ALREADY_IN_ROOM,    // Người dùng đang ở phòng khác
    NOT_IN_ROOM,        // Yêu cầu cần ở trong phòng mà chưa vào
    NOT_ROOM_OWNER,     // Cần là chủ phòng để thực hiện
    NOT_ENOUGH_PLAYERS, // Chưa đủ số người tối thiểu để bắt đầu
    NOT_ALL_READY,      // Có người chưa sẵn sàng

    // Ván / Game
    INVALID_STATE,      // Hành động sai trạng thái ván/phòng
    STALE_ROUND,        // roundId không khớp với ván hiện tại
    NOT_YOUR_TURN,      // Chưa đến lượt người chơi này
    INVALID_BET_AMOUNT, // Mức cược không hợp lệ (âm, vượt quá số dư...)

    // Chat
    MESSAGE_TOO_LONG,   // Vượt giới hạn 500 ký tự
    RATE_LIMIT_EXCEEDED,// Gửi quá 5 tin/10 giây

    // Dữ liệu
    INVALID_PAGE,       // Số trang hoặc pageSize không hợp lệ
}
