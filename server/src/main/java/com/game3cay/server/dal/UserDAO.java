package com.game3cay.server.dal;

import com.game3cay.server.bus.UserDTO;
import java.sql.*;

/** Truy vấn có tham số; hash chỉ tồn tại ở DAL/BUS, không nằm trong UserDTO. */
public final class UserDAO {
  public record Account(UserDTO profile, String passwordHash) {}

  private final ConnectionProvider connections;

  public UserDAO(ConnectionProvider connections) {
    this.connections = java.util.Objects.requireNonNull(connections);
  }

  public Account findByUsername(String username) throws SQLException {
    try (Connection c = connections.open();
        PreparedStatement s = c.prepareStatement("SELECT * FROM users WHERE username = ?")) {
      s.setString(1, username);
      try (ResultSet r = s.executeQuery()) {
        return r.next() ? read(r) : null;
      }
    }
  }

  public Account findById(long id) throws SQLException {
    try (Connection c = connections.open();
        PreparedStatement s = c.prepareStatement("SELECT * FROM users WHERE id = ?")) {
      s.setLong(1, id);
      try (ResultSet r = s.executeQuery()) {
        return r.next() ? read(r) : null;
      }
    }
  }

  public UserDTO insert(String username, String hash, String name, String email)
      throws SQLException {
    try (Connection c = connections.open();
        PreparedStatement s =
            c.prepareStatement(
                "INSERT INTO users (username,password_hash,display_name,email,balance) VALUES"
                    + " (?,?,?,?,1000.00)",
                Statement.RETURN_GENERATED_KEYS)) {
      s.setString(1, username);
      s.setString(2, hash);
      s.setString(3, name);
      s.setString(4, email);
      s.executeUpdate();
      try (ResultSet keys = s.getGeneratedKeys()) {
        if (!keys.next()) throw new SQLException("Thiếu ID tài khoản mới");
        return new UserDTO(
            keys.getLong(1),
            username,
            name,
            email,
            null,
            new java.math.BigDecimal("1000.00"),
            "ACTIVE",
            0,
            0,
            0);
      }
    }
  }

  public void updateProfile(long id, String name, String email, String avatar) throws SQLException {
    try (var c = connections.open();
        var s =
            c.prepareStatement(
                "UPDATE users SET display_name=COALESCE(?,display_name),email=CASE WHEN ? IS NULL"
                    + " THEN email ELSE NULLIF(?,'') END,avatar_url=COALESCE(?,avatar_url) WHERE"
                    + " id=?")) {
      s.setString(1, name);
      s.setString(2, email);
      s.setString(3, email);
      s.setString(4, avatar);
      s.setLong(5, id);
      if (s.executeUpdate() != 1) throw new SQLException("Không tìm thấy tài khoản");
    }
  }

  public boolean changePassword(long id, String expected, String replacement) throws SQLException {
    try (var c = connections.open();
        var s =
            c.prepareStatement("UPDATE users SET password_hash=? WHERE id=? AND password_hash=?")) {
      s.setString(1, replacement);
      s.setLong(2, id);
      s.setString(3, expected);
      return s.executeUpdate() == 1;
    }
  }

  private static Account read(ResultSet r) throws SQLException {
    return new Account(
        new UserDTO(
            r.getLong("id"),
            r.getString("username"),
            r.getString("display_name"),
            r.getString("email"),
            r.getString("avatar_url"),
            r.getBigDecimal("balance"),
            r.getString("status"),
            r.getInt("total_games"),
            r.getInt("total_wins"),
            r.getInt("total_losses")),
        r.getString("password_hash"));
  }
}
