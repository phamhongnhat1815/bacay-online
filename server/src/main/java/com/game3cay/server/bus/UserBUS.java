package com.game3cay.server.bus;

import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.AdminStatsSnapshot;
import com.game3cay.shared.model.AdminUserItem;
import com.game3cay.shared.model.PageResult;
import java.math.BigDecimal;

/**
 * Giao diện dịch vụ nghiệp vụ người dùng và tài khoản.
 */
public interface UserBUS {

    /**
     * Đăng ký tài khoản người chơi mới.
     *
     * @param username    tên đăng nhập (3–50 ký tự: chữ cái, chữ số, gạch dưới)
     * @param rawPassword mật khẩu thô của người dùng (tự động được băm an toàn)
     * @param displayName tên hiển thị (nếu để trống tự động lấy theo username)
     * @param email       địa chỉ email (tùy chọn)
     * @return {@link UserDTO} thông tin tài khoản vừa tạo
     * @throws BUSException nếu username đã tồn tại hoặc dữ liệu không hợp lệ
     */
    UserDTO register(String username, String rawPassword, String displayName, String email) throws BUSException;

    /**
     * Xác thực thông tin đăng nhập.
     *
     * @param username    tên đăng nhập
     * @param rawPassword mật khẩu thô
     * @return {@link UserDTO} nếu thông tin chính xác
     * @throws BUSException nếu sai thông tin hoặc tài khoản bị khóa
     */
    UserDTO authenticate(String username, String rawPassword) throws BUSException;

    /**
     * Lấy thông tin hồ sơ người dùng theo ID.
     *
     * @param userId mã ID người dùng
     * @return {@link UserDTO} hồ sơ người dùng
     * @throws BUSException nếu không tìm thấy người dùng
     */
    UserDTO getProfile(long userId) throws BUSException;

    /**
     * Cập nhật thông tin hồ sơ cá nhân.
     *
     * @param userId      mã ID người dùng
     * @param displayName tên hiển thị mới (nếu null giữ nguyên)
     * @param email       email mới (nếu null giữ nguyên)
     * @param avatarUrl   đường dẫn ảnh đại diện mới (nếu null giữ nguyên)
     * @throws BUSException nếu dữ liệu không hợp lệ hoặc email đã được dùng
     */
    void updateProfile(long userId, String displayName, String email, String avatarUrl) throws BUSException;

    /**
     * Đổi mật khẩu tài khoản.
     *
     * @param userId             mã ID người dùng
     * @param currentRawPassword mật khẩu hiện tại
     * @param newRawPassword     mật khẩu mới (8-128 ký tự)
     * @throws BUSException nếu mật khẩu cũ không đúng hoặc mật khẩu mới không hợp lệ
     */
    void changePassword(long userId, String currentRawPassword, String newRawPassword) throws BUSException;

    // --- Admin Operations ---

    /**
     * Admin tra cứu danh sách người dùng có phân trang và bộ lọc.
     */
    PageResult<AdminUserItem> findUsersForAdmin(String query, String statusFilter, int page, int pageSize) throws BUSException;

    /**
     * Admin khóa hoặc mở khóa tài khoản người dùng.
     */
    void updateUserStatusForAdmin(long userId, boolean blocked) throws BUSException;

    /**
     * Admin điều chỉnh số dư điểm (cộng/trừ) có ghi sổ cái kiểm toán.
     */
    void adjustBalanceForAdmin(long adminId, long targetUserId, BigDecimal delta, String reason) throws BUSException;

    /**
     * Admin lấy số liệu thống kê tổng thể toàn hệ thống.
     */
    AdminStatsSnapshot getSystemStatsForAdmin(long onlineUsers, int activeRooms) throws BUSException;

    /**
     * Admin tra cứu nhật ký sổ cái kép phân trang.
     */
    PageResult<AdminLedgerItem> getLedgerForAdmin(int page, int pageSize) throws BUSException;
}
