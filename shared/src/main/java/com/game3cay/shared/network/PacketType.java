package com.game3cay.shared.network;

/**
 * Toàn bộ loại thông điệp giữa Client ↔ Server.
 *
 * <p>Ký hiệu chiều:
 * <ul>
 *   <li>C→S : Client gửi lên Server
 *   <li>S→C : Server gửi xuống Client (broadcast hoặc riêng)
 *   <li>S→C* : chỉ gửi riêng đúng người nhận
 * </ul>
 *
 * <p>Mỗi type có payload tương ứng — xem {@code docs/Protocol.md} để biết chi tiết.
 */
public enum PacketType {

    // -------------------------------------------------------------------------
    // Hệ thống / chung
    // -------------------------------------------------------------------------

    /** S→C : Server PING, client phải trả PONG trong timeout. */
    PING,
    /** C→S : Client phản hồi PING. */
    PONG,
    /** S→C : Xác nhận thành công cho request của client (không có data riêng). */
    ACK,
    /** S→C : Phản hồi lỗi, data là {@link Packet.ErrorPayload}. */
    ERROR,

    // -------------------------------------------------------------------------
    // Xác thực (Auth) — C→S
    // -------------------------------------------------------------------------

    /** C→S : Đăng nhập. Payload: {@code AuthRequest}. */
    LOGIN,
    /** C→S : Đăng ký tài khoản. Payload: {@code RegisterRequest}. */
    REGISTER,
    /** C→S : Đăng xuất. Payload: null. */
    LOGOUT,
    /** S→C* : Kết quả AUTH (LOGIN / REGISTER). Payload: {@code AuthResult}. */
    AUTH_RESULT,

    // -------------------------------------------------------------------------
    // Hồ sơ (Profile)
    // -------------------------------------------------------------------------

    /** C→S : Lấy thông tin hồ sơ. Payload: null (lấy của mình) hoặc {@code Long userId}. */
    GET_PROFILE,
    /** S→C* : Kết quả hồ sơ. Payload: {@code ProfileResult}. */
    PROFILE_RESULT,
    /** C→S : Cập nhật tên hiển thị. Payload: {@code UpdateProfileRequest}. */
    UPDATE_PROFILE,
    /** C→S : Đổi mật khẩu. Payload: {@code ChangePasswordRequest}. */
    CHANGE_PASSWORD,

    // -------------------------------------------------------------------------
    // Lobby / Danh sách phòng
    // -------------------------------------------------------------------------

    /** C→S : Lấy danh sách phòng. Payload: null hoặc filter. */
    ROOM_LIST,
    /** S→C* : Kết quả danh sách phòng. Payload: {@code List<RoomSummary>}. */
    ROOM_LIST_RESULT,

    // -------------------------------------------------------------------------
    // Quản lý phòng
    // -------------------------------------------------------------------------

    /** C→S : Tạo phòng mới. Payload: {@code CreateRoomRequest}. */
    CREATE_ROOM,
    /** C→S : Tham gia phòng. Payload: {@code JoinRoomRequest}. */
    JOIN_ROOM,
    /** C→S : Rời phòng hoặc lobby. Payload: null. */
    LEAVE_ROOM,
    /** C→S : Bật/tắt trạng thái sẵn sàng. Payload: {@code Boolean ready}. */
    SET_READY,
    /** C→S : Chủ bàn đổi cược cơ bản khi bàn đang chờ. Payload: BigDecimal. */
    CHANGE_ROOM_BET,
    /** C→S : Chủ phòng yêu cầu bắt đầu ván. Payload: null. */
    START_GAME_REQUEST,
    /** S→C (broadcast trong phòng) : Trạng thái phòng thay đổi. Payload: {@code RoomSnapshot}. */
    ROOM_UPDATED,

    // -------------------------------------------------------------------------
    // Ván chơi
    // -------------------------------------------------------------------------

    /** S→C (broadcast) : Ván bắt đầu — thông báo công khai (không chứa bài). Payload: {@code GameStartInfo}. */
    START_GAME,
    /** S→C* : Chia bài riêng cho từng người — KHÔNG BAO GIỜ broadcast. Payload: {@code DealCardPayload}. */
    DEAL_CARD,
    /** C→S : Hành động trong ván (đặt cược, fold...). Payload: {@code GameActionRequest}. */
    GAME_ACTION,
    /** S→C (broadcast) : Cập nhật trạng thái công khai sau mỗi action. Payload: {@code GameStateSnapshot}. */
    GAME_UPDATED,
    /** S→C* : Trạng thái riêng của người nhận (bài, điểm cược, side pot của mình). Payload: {@code PlayerStateSnapshot}. */
    PLAYER_STATE,
    /** S→C (broadcast) : Kết quả ván — lật bài, xếp hạng, profit. Payload: {@code RoundResult}. */
    ROUND_RESULT,
    /** S→C (broadcast) : Trạng thái đặt cược hiện tại (pot, lượt, mức cược). Payload: {@code BetStateSnapshot}. */
    BET_UPDATED,

    // -------------------------------------------------------------------------
    // Chơi tiếp
    // -------------------------------------------------------------------------

    /** C→S : Vote chơi lại. Payload: {@code Boolean wantPlayAgain}. */
    PLAY_AGAIN_VOTE,
    /** S→C (broadcast) : Hỏi mọi người có muốn chơi lại không. Payload: {@code PlayAgainPrompt}. */
    PLAY_AGAIN_PROMPT,

    // -------------------------------------------------------------------------
    // Chat
    // -------------------------------------------------------------------------

    /** C→S : Gửi tin nhắn. S→C (broadcast trong phòng) : Tin nhắn đã xác nhận. Payload: {@code ChatMessage}. */
    CHAT_MESSAGE,
    /** S→C (broadcast) : Thông báo hệ thống trong phòng. Payload: {@code ChatMessage} với type=SYSTEM. */
    CHAT_SYSTEM,
    /** C→S : Mute người chơi (chỉ áp dụng phía client gửi). Payload: {@code Long targetUserId}. */
    MUTE_PLAYER,

    // -------------------------------------------------------------------------
    // Dữ liệu / Thống kê người chơi
    // -------------------------------------------------------------------------

    /** C→S : Lấy lịch sử ván của mình. Payload: {@code PageRequest}. */
    GET_HISTORY,
    /** S→C* : Kết quả lịch sử. Payload: {@code PageResult<GameHistoryItem>}. */
    HISTORY_RESULT,
    /** C→S : Lấy bảng xếp hạng. Payload: {@code PageRequest}. */
    GET_LEADERBOARD,
    /** S→C* : Kết quả bảng xếp hạng. Payload: {@code PageResult<LeaderboardItem>}. */
    LEADERBOARD_RESULT,
    ECHO_REQUEST,
    SERVER_NOTICE,

    // -------------------------------------------------------------------------
    // Quản trị hệ thống (Admin)
    // -------------------------------------------------------------------------

    /** C→S : Admin lấy số liệu thống kê realtime. Payload: null. */
    ADMIN_STATS_REQ,
    /** S→C* : Trả về số liệu thống kê. Payload: {@code AdminStatsSnapshot}. */
    ADMIN_STATS_RESP,

    /** C→S : Admin tra cứu danh sách người dùng. Payload: {@code AdminUserFilterRequest}. */
    ADMIN_USERS_PAGE_REQ,
    /** S→C* : Trả về danh sách người dùng. Payload: {@code PageResult<AdminUserItem>}. */
    ADMIN_USERS_PAGE_RESP,

    /** C→S : Admin khóa / mở khóa tài khoản. Payload: {@code AdminLockUserRequest}. */
    ADMIN_LOCK_USER_REQ,
    /** C→S : Admin cộng / trừ điểm người dùng. Payload: {@code AdminAdjustBalanceRequest}. */
    ADMIN_ADJUST_BALANCE_REQ,

    /** C→S : Admin lấy chi tiết giám sát 20 bàn chơi. Payload: null. */
    ADMIN_ROOMS_MONITOR_REQ,
    /** S→C* : Trả về chi tiết 20 bàn chơi. Payload: {@code List<AdminRoomDetail>}. */
    ADMIN_ROOMS_MONITOR_RESP,

    /** C→S : Admin cưỡng chế giải tán / hủy bàn kẹt. Payload: {@code AdminResetRoomRequest}. */
    ADMIN_FORCE_RESET_ROOM,

    /** C→S : Admin soi chi tiết ván bài thời gian thực (God-View). Payload: Long roomId. */
    ADMIN_INSPECT_ROOM_REQ,
    /** S→C* : Trả về chi tiết ván bài cho Admin (God-View). Payload: {@code AdminLiveMatchDetail}. */
    ADMIN_INSPECT_ROOM_RESP,

    /** C→S : Admin phát thông báo toàn server. Payload: {@code AdminBroadcastRequest}. */
    ADMIN_BROADCAST_REQ,

    /** C→S : Admin tra cứu nhật ký sổ cái kiểm toán. Payload: {@code PageRequest}. */
    ADMIN_LEDGER_PAGE_REQ,
    /** S→C* : Trả về danh sách nhật ký sổ cái. Payload: {@code PageResult<AdminLedgerItem>}. */
    ADMIN_LEDGER_PAGE_RESP
}
