package com.game3cay.server.bus;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.dal.DatabaseConfig;
import com.game3cay.server.dal.UserDAO;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.security.PasswordUtil;
import java.sql.SQLException;
import java.util.Locale;

/** Quản lý tài khoản và kiểm tra dữ liệu trước khi truy cập MySQL. */
public final class JdbcUserBUS implements UserBUS {
  private final UserDAO users;

  public JdbcUserBUS(ConnectionProvider connections) {
    users = new UserDAO(connections);
  }

  public static JdbcUserBUS fromEnvironment() {
    return new JdbcUserBUS(DatabaseConfig.fromEnvironment());
  }

  @Override
  public UserDTO register(String username, String rawPassword, String displayName, String email)
      throws BUSException {
    String normalized = normalizeUsername(username);
    if (rawPassword == null
        || rawPassword.length() < 8
        || rawPassword.length() > 128
        || rawPassword.isBlank()) {
      throw new BUSException(ErrorCode.INVALID_REQUEST, "Mật khẩu cần 8–128 ký tự");
    }
    String name = displayName == null || displayName.isBlank() ? normalized : displayName.trim();
    if (name.length() > 100) throw invalid("Tên hiển thị tối đa 100 ký tự");
    String mail = email == null || email.isBlank() ? null : email.trim();
    if (mail != null && (mail.length() > 100 || !mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
      throw invalid("Email không hợp lệ");
    }
    try {
      return users.insert(normalized, PasswordUtil.hash(rawPassword), name, mail);
    } catch (SQLException e) {
      if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
        try {
          if (users.findByUsername(normalized) != null) {
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
        || !username.trim().matches("[A-Za-z0-9_]{3,50}")
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
        throw new BUSException(ErrorCode.USER_BLOCKED, "Tài khoản bị khóa");
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
    if (userId <= 0) throw invalid("ID tài khoản không hợp lệ");
    try {
      UserDAO.Account account = users.findById(userId);
      if (account == null)
        throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không tìm thấy tài khoản");
      return account.profile();
    } catch (SQLException e) {
      throw databaseError(e);
    }
  }

  @Override
  public void updateProfile(long userId, String displayName, String email, String avatarUrl)
      throws BUSException {
    if (displayName != null && (displayName.isBlank() || displayName.length() > 100))
      throw invalid("Tên hiển thị 1–100 ký tự");
    if (email != null
        && !email.isBlank()
        && (email.length() > 100 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
      throw invalid("Email không hợp lệ");
    if (avatarUrl != null && avatarUrl.length() > 255)
      throw invalid("Địa chỉ ảnh tối đa 255 ký tự");
    try {
      users.updateProfile(
          userId,
          displayName == null ? null : displayName.trim(),
          email == null ? null : email.trim(),
          avatarUrl);
    } catch (SQLException e) {
      if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState()))
        throw invalid("Email đã được sử dụng");
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
        || newRawPassword.length() > 128) throw invalid("Mật khẩu mới cần 8–128 ký tự");
    try {
      var account = users.findById(userId);
      if (account == null || !PasswordUtil.verify(currentRawPassword, account.passwordHash()))
        throw new BUSException(ErrorCode.FORBIDDEN, "Mật khẩu hiện tại không đúng");
      if (!users.changePassword(userId, account.passwordHash(), PasswordUtil.hash(newRawPassword)))
        throw new BUSException(ErrorCode.INVALID_STATE, "Mật khẩu vừa thay đổi ở yêu cầu khác");
    } catch (SQLException e) {
      throw databaseError(e);
    }
  }

  private static String normalizeUsername(String username) throws BUSException {
    if (username == null || !username.trim().matches("[A-Za-z0-9_]{3,50}")) {
      throw invalid("Tên đăng nhập cần 3–50 ký tự chữ, số hoặc gạch dưới");
    }
    return username.trim().toLowerCase(Locale.ROOT);
  }

  private static BUSException invalid(String message) {
    return new BUSException(ErrorCode.INVALID_REQUEST, message);
  }

  private static BUSException credentials() {
    return new BUSException(
        ErrorCode.INVALID_CREDENTIALS, "Tên đăng nhập hoặc mật khẩu không đúng");
  }

  private static BUSException databaseError(SQLException cause) {
    return new BUSException(
        ErrorCode.INTERNAL_ERROR, "Không thể truy cập dữ liệu tài khoản", cause);
  }
}
