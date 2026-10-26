package com.game3cay.server.bus;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.shared.model.AdminPayload;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.security.PasswordUtil;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class AdminService {
  private static final BigDecimal MAX = new BigDecimal("9999999999999.99");
  private final ConnectionProvider connections;

  public AdminService(ConnectionProvider connections) {
    this.connections = connections;
  }

  public void checkSchema() throws SQLException {
    try (var c = connections.open(); var s = c.createStatement()) {
      s.executeQuery("SELECT role FROM users WHERE 1=0").close();
      s.executeQuery("SELECT request_id FROM admin_audit WHERE 1=0").close();
    }
  }

  public String roleOf(long uid) throws BUSException {
    try (var c = connections.open();
        var q = c.prepareStatement(
            "SELECT role,status FROM users WHERE id=?")) {
      q.setLong(1, uid);

      try (var r = q.executeQuery()) {
        if (!r.next() || !"ACTIVE".equals(r.getString(2))) {
          throw fail(
              ErrorCode.FORBIDDEN, "Tài khoản không còn hoạt động");
        }
        return "ADMIN".equals(r.getString(1)) ? "ADMIN" : "PLAYER";
      }
    } catch (SQLException e) {
      throw database(e);
    }
  }

  public void requireAdmin(long uid) throws BUSException {
    if (!"ADMIN".equals(roleOf(uid))) {
      throw fail(
          ErrorCode.FORBIDDEN,
          "Chỉ admin được thực hiện thao tác này");
    }
  }

  public AdminPayload.Response list(long actor, int page, String search)
      throws BUSException {
    requireAdmin(actor);

    if (page < 0 || page > 1_000_000) {
      throw fail(ErrorCode.INVALID_PAGE, "Trang không hợp lệ");
    }

    String text =
        search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

    String filter =
        " WHERE LOCATE(?,LOWER(username))>0"
            + " OR LOCATE(?,LOWER(display_name))>0";

    try (var c = connections.open()) {
      long count;

      try (var q = c.prepareStatement("SELECT COUNT(*) FROM users" + filter)) {
        q.setString(1, text);
        q.setString(2, text);
        try (var r = q.executeQuery()) {
          r.next();
          count = r.getLong(1);
        }
      }

      var accounts = new ArrayList<AdminPayload.Account>();

      try (var q = c.prepareStatement(
          "SELECT id,username,display_name,role,status,balance FROM users"
              + filter + " ORDER BY id LIMIT 20 OFFSET ?")) {
        q.setString(1, text);
        q.setString(2, text);
        q.setLong(3, (long) page * 20);

        try (var r = q.executeQuery()) {
          while (r.next()) {
            accounts.add(
                new AdminPayload.Account(
                    r.getLong(1), r.getString(2), r.getString(3),
                    r.getString(4), r.getString(5), r.getBigDecimal(6)));
          }
        }
      }

      return new AdminPayload.Response(
          "Danh sách tài khoản",
          List.copyOf(accounts),
          page,
          Math.max(1, (int) ((count + 19) / 20)));
    } catch (SQLException e) {
      throw database(e);
    }
  }

  public void change(
      long actor, String requestId, AdminPayload.Request request)
      throws BUSException {
    requireAdmin(actor);

    if (request.userId() <= 0
        || requestId == null
        || requestId.length() > 64) {
      throw fail(
          ErrorCode.INVALID_REQUEST, "Thiếu tài khoản hoặc mã thao tác");
    }

    String hash = null;

    if (request.action() == AdminPayload.Action.RESET_PASSWORD) {
      if (request.value() == null
          || request.value().isBlank()
          || request.value().length() < 8
          || request.value().length() > 128) {
        throw fail(
            ErrorCode.INVALID_REQUEST, "Mật khẩu mới cần 8–128 ký tự");
      }
      hash = PasswordUtil.hash(request.value());
    }

    try (var c = connections.open()) {
      c.setAutoCommit(false);

      try {
        String targetRole = null;
        String targetStatus = null;
        BigDecimal before = null;

        // Lock theo ID tăng dần.
        for (long id :
            java.util.stream.LongStream.of(actor, request.userId())
                .distinct().sorted().toArray()) {
          try (var q = c.prepareStatement(
              "SELECT role,status,balance FROM users WHERE id=? FOR UPDATE")) {
            q.setLong(1, id);

            try (var r = q.executeQuery()) {
              if (!r.next()) {
                throw fail(
                    ErrorCode.INVALID_REQUEST, "Không tìm thấy tài khoản");
              }

              String role = r.getString(1);
              String status = r.getString(2);

              if (id == actor
                  && (!"ADMIN".equals(role) || !"ACTIVE".equals(status))) {
                throw fail(
                    ErrorCode.FORBIDDEN, "Tài khoản không còn quyền admin");
              }

              if (id == request.userId()) {
                targetRole = role;
                targetStatus = status;
                before = r.getBigDecimal(3);
              }
            }
          }
        }

        // Cùng requestId không được cấp điểm lần thứ hai.
        try (var q = c.prepareStatement(
            "SELECT actor_id,target_id,action,amount,details"
                + " FROM admin_audit WHERE request_id=?")) {
          q.setString(1, requestId);

          try (var r = q.executeQuery()) {
            if (r.next()) {
              boolean amountMatches =
                  request.amount() == null
                      ? r.getBigDecimal(4) == null
                      : r.getBigDecimal(4) != null
                          && request.amount().compareTo(r.getBigDecimal(4)) == 0;

              String detail =
                  request.action() == AdminPayload.Action.SET_BLOCKED
                      ? request.value() : "";

              if (r.getLong(1) != actor
                  || r.getLong(2) != request.userId()
                  || !request.action().name().equals(r.getString(3))
                  || !amountMatches
                  || !detail.equals(r.getString(5))) {
                throw fail(
                    ErrorCode.INVALID_REQUEST, "Mã thao tác đã được dùng");
              }

              c.rollback();
              return;
            }
          }
        }

        BigDecimal after = before;

        switch (request.action()) {
          case CREDIT -> {
            BigDecimal n = request.amount();

            if (n == null
                || n.signum() <= 0
                || n.scale() < 0
                || n.scale() > 2
                || n.compareTo(MAX) > 0
                || before.add(n).compareTo(MAX) > 0) {
              throw fail(
                  ErrorCode.INVALID_REQUEST,
                  "Điểm cấp phải dương và không vượt giới hạn số dư");
            }

            if (!"ACTIVE".equals(targetStatus)) {
              throw fail(
                  ErrorCode.INVALID_STATE, "Tài khoản đang bị khóa");
            }

            after = before.add(n);

            update(
                c, "UPDATE users SET balance=? WHERE id=?",
                after, request.userId());
          }

          case SET_BLOCKED -> {
            if ("ADMIN".equals(targetRole)) {
              throw fail(
                  ErrorCode.FORBIDDEN, "Không khóa tài khoản admin");
            }

            if (!"ACTIVE".equals(request.value())
                && !"BLOCKED".equals(request.value())) {
              throw fail(
                  ErrorCode.INVALID_REQUEST, "Trạng thái không hợp lệ");
            }

            update(
                c, "UPDATE users SET status=? WHERE id=?",
                request.value(), request.userId());
          }

          case RESET_PASSWORD -> {
            if ("ADMIN".equals(targetRole)) {
              throw fail(
                  ErrorCode.FORBIDDEN,
                  "Không đặt lại mật khẩu admin từ chức năng này");
            }

            update(
                c, "UPDATE users SET password_hash=? WHERE id=?",
                hash, request.userId());
          }

          default ->
              throw fail(
                  ErrorCode.INVALID_REQUEST, "Thao tác không hợp lệ");
        }

        update(
            c,
            "INSERT INTO admin_audit"
                + "(request_id,actor_id,target_id,action,amount,"
                + "balance_before,balance_after,details)"
                + " VALUES(?,?,?,?,?,?,?,?)",
            requestId,
            actor,
            request.userId(),
            request.action().name(),
            request.amount(),
            before,
            after,
            request.action() == AdminPayload.Action.SET_BLOCKED
                ? request.value() : "");

        c.commit();
      } catch (Exception e) {
        c.rollback();
        if (e instanceof BUSException b) {
          throw b;
        }
        throw database(e);
      }
    } catch (SQLException e) {
      throw database(e);
    }
  }

  private static void update(Connection c, String sql, Object... args)
      throws SQLException {
    try (var q = c.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) {
        q.setObject(i + 1, args[i]);
      }
      q.executeUpdate();
    }
  }

  private static BUSException fail(ErrorCode code, String message) {
    return new BUSException(code, message);
  }

  private static BUSException database(Exception e) {
    return new BUSException(
        ErrorCode.INTERNAL_ERROR,
        "Không truy cập được dữ liệu quản trị", e);
  }
}