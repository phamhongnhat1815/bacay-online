package com.bacay.shared.network;

/**
 * Toàn bộ loại thông điệp giữa Client ↔ Server.
 *
 * <p>Ký hiệu chiều:
 * <ul>
 *   <li>C→S : Client gửi lên Server</li>
 *   <li>S→C : Server gửi xuống Client (broadcast hoặc riêng)</li>
 *   <li>S→C* : chỉ gửi riêng đúng người nhận</li>
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
    /** C→S : Chủ phòng yêu cầu bắt đầu ván. Payload: null. */
    START_GAME_REQUEST,
    /**
     * S→C (broadcast trong phòng) : Trạng thái phòng thay đổi.
     * Payload: {@code RoomSnapshot}.
     */
    ROOM_UPDATED,

    // -------------------------------------------------------------------------
    // Ván chơi
    // -------------------------------------------------------------------------

    /**
     * S→C (broadcast) : Ván bắt đầu — thông báo công khai (không chứa bài).
     * Payload: {@code GameStartInfo}.
     */
    START_GAME,
    /**
     * S→C* : Chia bài riêng cho từng người — KHÔNG BAO GIỜ broadcast.
     * Payload: {@code DealCardPayload}.
     */
    DEAL_CARD,
    /**
     * C→S : Hành động trong ván (đặt cược, fold...).
     * Payload: {@code GameActionRequest}.
     */
    GAME_ACTION,
    /**
     * S→C (broadcast) : Cập nhật trạng thái công khai sau mỗi action.
     * Payload: {@code GameStateSnapshot}.
     */
    GAME_UPDATED,
    /**
     * S→C* : Trạng thái riêng của người nhận (bài, điểm cược, side pot của mình).
     * Payload: {@code PlayerStateSnapshot}.
     */
    PLAYER_STATE,
    /**
     * S→C (broadcast) : Kết quả ván — lật bài, xếp hạng, profit.
     * Payload: {@code RoundResult}.
     */
    ROUND_RESULT,
    /** S→C (broadcast) : Trạng thái đặt cược hiện tại (pot, lượt, mức cược).
     * Payload: {@code BetStateSnapshot}. */
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

    /**
     * C→S : Gửi tin nhắn văn bản hoặc emoji.
     * S→C (broadcast trong phòng) : Tin nhắn đã được server xác nhận và phát.
     * Payload: {@code ChatMessage}.
     */
    CHAT_MESSAGE,
    /** S→C (broadcast) : Thông báo hệ thống (vào/rời phòng, bắt đầu/kết thúc ván...).
     * Payload: {@code ChatMessage} với type=SYSTEM. */
    CHAT_SYSTEM,
    /** C→S : Mute người chơi (chỉ áp dụng phía client gửi). Payload: {@code Long targetUserId}. */
    MUTE_PLAYER,

    // -------------------------------------------------------------------------
    // Dữ liệu / Thống kê
    // -------------------------------------------------------------------------

    /** C→S : Lấy lịch sử ván của mình. Payload: {@code PageRequest}. */
    GET_HISTORY,
    /** S→C* : Kết quả lịch sử. Payload: {@code PageResult<GameHistoryItem>}. */
    HISTORY_RESULT,
    /** C→S : Lấy bảng xếp hạng. Payload: {@code PageRequest}. */
    GET_LEADERBOARD,
    /** S→C* : Kết quả bảng xếp hạng. Payload: {@code PageResult<LeaderboardItem>}. */
    LEADERBOARD_RESULT,
}
