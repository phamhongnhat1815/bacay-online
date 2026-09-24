package com.bacay.server.bus;

import com.bacay.shared.network.ErrorCode;

/**
 * Hợp đồng BUS cho nghiệp vụ người dùng — do <b>Nhật</b> triển khai (module D03).
 *
 * <p><b>Khánh</b> (N03) gọi các phương thức này từ handler xử lý packet
 * {@code LOGIN}, {@code REGISTER}, {@code GET_PROFILE}, {@code UPDATE_PROFILE},
 * {@code CHANGE_PASSWORD}.
 *
 * <h2>Quy tắc ErrorCode</h2>
 * <ul>
 *   <li>{@link ErrorCode#USERNAME_EXISTS} — {@link #register} khi username đã tồn tại</li>
 *   <li>{@link ErrorCode#INVALID_CREDENTIALS} — {@link #authenticate} khi sai username/password</li>
 *   <li>{@link ErrorCode#USER_BLOCKED} — tài khoản bị khóa</li>
 *   <li>{@link ErrorCode#FORBIDDEN} — đổi mật khẩu nhưng mật khẩu hiện tại sai</li>
 *   <li>{@link ErrorCode#INTERNAL_ERROR} — lỗi DB không lường trước</li>
 * </ul>
 *
 * <h2>Quy tắc triển khai (Nhật)</h2>
 * <ul>
 *   <li>Hash mật khẩu bằng {@code PasswordUtil.hash()} trong {@code shared/security/}.</li>
 *   <li>Verify bằng {@code PasswordUtil.verify()} — không tự viết lại.</li>
 *   <li>Không trả về thông tin password_hash trong {@link UserDTO}.</li>
 *   <li>Các phương thức KHÔNG cần synchronized — JDBC connection pool tự quản.</li>
 * </ul>
 */
public interface UserBUS {

    /**
     * Đăng ký tài khoản mới.
     *
     * @param username    3–50 ký tự, chỉ chữ/số/gạch dưới
     * @param rawPassword mật khẩu plain text (BUS tự hash, không lưu plain text)
     * @param displayName tên hiển thị (null → dùng username)
     * @param email       email (null → bỏ qua)
     * @return {@link UserDTO} của tài khoản vừa tạo
     * @throws BUSException {@link ErrorCode#USERNAME_EXISTS} nếu username đã tồn tại;
     *                      {@link ErrorCode#INTERNAL_ERROR} nếu lỗi DB
     */
    UserDTO register(String username, String rawPassword,
                     String displayName, String email) throws BUSException;

    /**
     * Xác thực đăng nhập.
     *
     * @return {@link UserDTO} nếu thành công
     * @throws BUSException {@link ErrorCode#INVALID_CREDENTIALS} nếu sai;
     *                      {@link ErrorCode#USER_BLOCKED} nếu bị khóa
     */
    UserDTO authenticate(String username, String rawPassword) throws BUSException;

    /**
     * Lấy hồ sơ người dùng theo userId.
     *
     * @throws BUSException {@link ErrorCode#INTERNAL_ERROR} nếu không tìm thấy hoặc lỗi DB
     */
    UserDTO getProfile(long userId) throws BUSException;

    /**
     * Cập nhật thông tin hồ sơ. Trường nào {@code null} → giữ nguyên.
     *
     * @throws BUSException {@link ErrorCode#INTERNAL_ERROR} nếu lỗi DB
     */
    void updateProfile(long userId, String displayName,
                       String email, String avatarUrl) throws BUSException;

    /**
     * Đổi mật khẩu.
     *
     * @param currentRawPassword mật khẩu hiện tại (để verify)
     * @param newRawPassword     mật khẩu mới (BUS tự hash)
     * @throws BUSException {@link ErrorCode#FORBIDDEN} nếu mật khẩu hiện tại sai;
     *                      {@link ErrorCode#INTERNAL_ERROR} nếu lỗi DB
     */
    void changePassword(long userId, String currentRawPassword,
                        String newRawPassword) throws BUSException;

    /**
     * Cập nhật số dư sau ván chơi (ghi transaction).
     * Khánh không gọi trực tiếp — GameBUS gọi nội bộ hoặc Nhật tự quản.
     *
     * <p><b>Quy tắc bắt buộc (Nhật triển khai):</b>
     * <ol>
     *   <li>Truy vấn {@code SELECT balance FROM users WHERE id = userId FOR UPDATE} trước.</li>
     *   <li>Nếu {@code currentBalance + delta < 0} → ném ngay
     *       {@link ErrorCode#INVALID_BET_AMOUNT} — KHÔNG để DB constraint bắt lỗi thay.</li>
     *   <li>Ghi {@code transactions} rồi mới {@code UPDATE users SET balance = balance + delta}.</li>
     *   <li>DB cũng có {@code CHECK (balance >= 0)} làm lớp bảo vệ thứ hai.</li>
     * </ol>
     *
     * @param delta dương = cộng điểm, âm = trừ điểm
     * @throws BUSException {@link ErrorCode#INVALID_BET_AMOUNT} nếu kết quả sẽ âm;
     *                      {@link ErrorCode#INTERNAL_ERROR} nếu lỗi DB
     */
    void updateBalance(long userId, java.math.BigDecimal delta) throws BUSException;
}
