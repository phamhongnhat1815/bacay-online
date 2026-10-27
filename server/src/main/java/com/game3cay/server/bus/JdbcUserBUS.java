package com.game3cay.server.bus;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.dal.DatabaseConfig;
import com.game3cay.server.dal.UserDAO;
import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.AdminStatsSnapshot;
import com.game3cay.shared.model.AdminUserItem;
import com.game3cay.shared.model.PageResult;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.security.PasswordUtil;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cài đặt {@link UserBUS} sử dụng JDBC và MySQL.
 * <p>
 * Đảm nhận kiểm tra hợp lệ dữ liệu đầu vào (Validation) trước khi truy cập CSDL,
 * băm mật khẩu bằng PBKDF2 và phân loại lỗi SQL thành {@link BUSException}.
 */
public final class JdbcUserBUS implements UserBUS {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,50}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserDAO users;

    public JdbcUserBUS(ConnectionProvider connections) {
        Objects.requireNonNull(connections, "connections không được null");
        this.users = new UserDAO(connections);
    }

    public static JdbcUserBUS fromEnvironment() {
        return new JdbcUserBUS(DatabaseConfig.fromEnvironment());
    }

    @Override
    public UserDTO register(String username, String rawPassword, String displayName, String email)
            throws BUSException {
        String normalizedUsername = normalizeUsername(username);

        if (normalizedUsername.equals("admin")
                || normalizedUsername.startsWith("admin_")
                || normalizedUsername.equals("administrator")
                || normalizedUsername.equals("quantrivien")
                || normalizedUsername.equals("root")
                || normalizedUsername.equals("system")) {
            throw invalid("Tên đăng nhập này được dành riêng cho hệ thống");
        }

        if (rawPassword == null
                || rawPassword.length() < 8
                || rawPassword.length() > 128
                || rawPassword.isBlank()) {
            throw new BUSException(ErrorCode.INVALID_REQUEST, "Mật khẩu cần từ 8 đến 128 ký tự");
        }

        String name = (displayName == null || displayName.isBlank())
                ? normalizedUsername
                : displayName.trim();

        if (name.length() > 100) {
            throw invalid("Tên hiển thị tối đa 100 ký tự");
        }

        String mail = (email == null || email.isBlank()) ? null : email.trim();
        if (mail != null && (mail.length() > 100 || !EMAIL_PATTERN.matcher(mail).matches())) {
            throw invalid("Email không đúng định dạng");
        }

        try {
            String passwordHash = PasswordUtil.hash(rawPassword);
            return users.insert(normalizedUsername, passwordHash, name, mail);
        } catch (SQLException e) {
            // MySQL error 1062 hoặc SQLState 23505 (Unique violation)
            if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                try {
                    if (users.findByUsername(normalizedUsername) != null) {
                        throw new BUSException(ErrorCode.USERNAME_EXISTS, "Tên đăng nhập đã tồn tại");
                    }
                } catch (SQLException lookupError) {
                    throw databaseError(lookupError);
                }
                throw invalid("Email đã được sử dụng");
            }
            throw databaseError(e);
        }
    }

    @Override
    public UserDTO authenticate(String username, String rawPassword) throws BUSException {
        if (username == null
                || !USERNAME_PATTERN.matcher(username.trim()).matches()
                || rawPassword == null
                || rawPassword.isBlank()
                || rawPassword.length() > 128) {
            throw credentials();
        }

        try {
            UserDAO.Account account = users.findByUsername(normalizeUsername(username));
            if (account == null || !PasswordUtil.verify(rawPassword, account.passwordHash())) {
                throw credentials();
            }

            if ("BLOCKED".equals(account.profile().status())) {
                throw new BUSException(ErrorCode.USER_BLOCKED, "Tài khoản của bạn đã bị khóa");
            }

            if (!"ACTIVE".equals(account.profile().status())) {
                throw new BUSException(ErrorCode.INTERNAL_ERROR, "Trạng thái tài khoản không hợp lệ");
            }

            return account.profile();
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public UserDTO getProfile(long userId) throws BUSException {
        if (userId <= 0) {
            throw invalid("ID tài khoản không hợp lệ");
        }

        try {
            UserDAO.Account account = users.findById(userId);
            if (account == null) {
                throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không tìm thấy thông tin tài khoản");
            }
            return account.profile();
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public void updateProfile(long userId, String displayName, String email, String avatarUrl)
            throws BUSException {
        if (displayName != null && (displayName.isBlank() || displayName.length() > 100)) {
            throw invalid("Tên hiển thị cần từ 1 đến 100 ký tự");
        }
        if (email != null && !email.isBlank()) {
            if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
                throw invalid("Email không đúng định dạng");
            }
        }
        if (avatarUrl != null && avatarUrl.length() > 255) {
            throw invalid("Địa chỉ ảnh tối đa 255 ký tự");
        }

        try {
            users.updateProfile(
                    userId,
                    displayName == null ? null : displayName.trim(),
                    email == null ? null : email.trim(),
                    avatarUrl
            );
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                throw invalid("Email đã được sử dụng bởi tài khoản khác");
            }
            throw databaseError(e);
        }
    }

    @Override
    public void changePassword(long userId, String currentRawPassword, String newRawPassword)
            throws BUSException {
        if (currentRawPassword == null
                || newRawPassword == null
                || newRawPassword.isBlank()
                || newRawPassword.length() < 8
                || newRawPassword.length() > 128) {
            throw invalid("Mật khẩu mới cần từ 8 đến 128 ký tự");
        }

        try {
            UserDAO.Account account = users.findById(userId);
            if (account == null || !PasswordUtil.verify(currentRawPassword, account.passwordHash())) {
                throw new BUSException(ErrorCode.FORBIDDEN, "Mật khẩu hiện tại không đúng");
            }

            String newHash = PasswordUtil.hash(newRawPassword);
            boolean updated = users.changePassword(userId, account.passwordHash(), newHash);
            if (!updated) {
                throw new BUSException(ErrorCode.INVALID_STATE, "Mật khẩu vừa thay đổi ở một phiên khác");
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public PageResult<AdminUserItem> findUsersForAdmin(String query, String statusFilter, int page, int pageSize)
            throws BUSException {
        try {
            return users.findUsersForAdmin(query, statusFilter, page, pageSize);
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public void updateUserStatusForAdmin(long userId, boolean blocked) throws BUSException {
        try {
            String newStatus = blocked ? "BLOCKED" : "ACTIVE";
            boolean updated = users.updateUserStatus(userId, newStatus);
            if (!updated) {
                throw new BUSException(ErrorCode.INVALID_REQUEST, "Không tìm thấy người dùng với ID: " + userId);
            }
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public void adjustBalanceForAdmin(long adminId, long targetUserId, BigDecimal delta, String reason)
            throws BUSException {
        if (delta == null || delta.signum() == 0 || delta.scale() > 2) {
            throw new BUSException(ErrorCode.INVALID_BET_AMOUNT, "Số điểm điều chỉnh không hợp lệ");
        }
        try {
            users.adjustUserBalance(adminId, targetUserId, delta, reason);
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public AdminStatsSnapshot getSystemStatsForAdmin(long onlineUsers, int activeRooms) throws BUSException {
        try {
            return users.getAdminStats(onlineUsers, activeRooms);
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    @Override
    public PageResult<AdminLedgerItem> getLedgerForAdmin(int page, int pageSize) throws BUSException {
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new BUSException(ErrorCode.INVALID_PAGE, "Tham số phân trang không hợp lệ");
        }
        try {
            return users.getLedgerEntries(page, pageSize);
        } catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private static String normalizeUsername(String username) throws BUSException {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw invalid("Tên đăng nhập cần từ 3 đến 50 ký tự chữ, số hoặc gạch dưới");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static BUSException invalid(String message) {
        return new BUSException(ErrorCode.INVALID_REQUEST, message);
    }

    private static BUSException credentials() {
        return new BUSException(ErrorCode.INVALID_CREDENTIALS, "Tên đăng nhập hoặc mật khẩu không chính xác");
    }

    private static BUSException databaseError(SQLException cause) {
        return new BUSException(ErrorCode.INTERNAL_ERROR, "Không thể truy cập cơ sở dữ liệu", cause);
    }
}
