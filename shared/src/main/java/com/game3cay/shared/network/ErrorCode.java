package com.game3cay.shared.network;

/**
 * Danh mục mã lỗi nghiệp vụ chuẩn trong toàn hệ thống Ba Cây Online.
 * <p>
 * Phía Client chỉ kiểm tra mã lỗi theo enum này (hoặc tên enum),
 * không thực hiện phân nhánh nghiệp vụ bằng chuỗi thông báo (message).
 */
public enum ErrorCode {

    // --- Lỗi chung hệ thống ---
    /** Yêu cầu sai cú pháp, thiếu trường dữ liệu hoặc vượt quá giới hạn. */
    INVALID_REQUEST,
    /** Chưa đăng nhập mà thực hiện thao tác yêu cầu định danh. */
    UNAUTHENTICATED,
    /** Đã đăng nhập nhưng không có thẩm quyền thực hiện thao tác này. */
    FORBIDDEN,
    /** Lỗi nội bộ không mong muốn từ phía server. */
    INTERNAL_ERROR,
    /** Chức năng chưa được cài đặt hoặc hỗ trợ. */
    NOT_IMPLEMENTED,

    // --- Lỗi tài khoản & xác thực ---
    /** Tên đăng nhập đã có người đăng ký. */
    USERNAME_EXISTS,
    /** Sai tên đăng nhập hoặc mật khẩu. */
    INVALID_CREDENTIALS,
    /** Tài khoản đang đăng nhập trên một phiên kết nối khác. */
    ALREADY_LOGGED_IN,
    /** Tài khoản đã bị khóa hoặc ngừng hoạt động. */
    USER_BLOCKED,

    // --- Lỗi phòng & bàn chơi ---
    /** Bàn chơi không tồn tại hoặc đã bị hủy. */
    ROOM_NOT_FOUND,
    /** Bàn chơi đã đủ số lượng người tối đa (8 người). */
    ROOM_FULL,
    /** Bàn chơi đang diễn ra ván bài, tạm thời không nhận thêm người vào. */
    ROOM_NOT_WAITING,
    /** Người chơi đã ở trong một bàn chơi khác. */
    ALREADY_IN_ROOM,
    /** Người chơi chưa tham gia bàn nào để thực hiện thao tác. */
    NOT_IN_ROOM,
    /** Thao tác chỉ dành cho chủ bàn (ví dụ: bắt đầu ván, đổi cược). */
    NOT_ROOM_OWNER,
    /** Chưa đủ số lượng người chơi tối thiểu (tối thiểu 2 người đủ điểm). */
    NOT_ENOUGH_PLAYERS,
    /** Còn người chơi trong bàn chưa bấm sẵn sàng. */
    NOT_ALL_READY,

    // --- Lỗi trạng thái ván bài & lượt chơi ---
    /** Trạng thái hiện tại của ván hoặc phòng không cho phép thực hiện thao tác. */
    INVALID_STATE,
    /** Mã ván (roundId) không khớp với ván đang diễn ra. */
    STALE_ROUND,
    /** Chưa tới lượt hành động của người chơi này. */
    NOT_YOUR_TURN,
    /** Mức điểm cược không hợp lệ (âm, nhỏ hơn mức cược bàn, hoặc vượt quá số dư). */
    INVALID_BET_AMOUNT,

    // --- Lỗi trò chuyện (Chat) ---
    /** Nội dung tin nhắn vượt quá độ dài tối đa (500 ký tự). */
    MESSAGE_TOO_LONG,
    /** Gửi tin nhắn quá nhanh (vượt quá giới hạn 5 tin / 10 giây). */
    RATE_LIMIT_EXCEEDED,

    // --- Lỗi dữ liệu & phân trang ---
    /** Số trang hoặc kích thước trang không hợp lệ (trang >= 0, size từ 1..100). */
    INVALID_PAGE
}
