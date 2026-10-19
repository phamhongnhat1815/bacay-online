package com.game3cay.server.bus;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.game.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.ErrorCode;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class JdbcGameBUS implements GameBUS {
  private final ConnectionProvider connections;

  public JdbcGameBUS(ConnectionProvider c) {
    connections = Objects.requireNonNull(c);
  }

  public void checkSchema() throws SQLException {
    try (var c = connections.open();
        var s = c.createStatement()) {
      s.executeQuery("SELECT round_id FROM play_rounds WHERE 1=0").close();
    }
  }

  @Override
  public void saveCompleted(CompletedRound completed) throws BUSException {
    var r = completed.result();
    String hash = fingerprint(completed);
    if (!Set.of("FINISHED", "CANCELLED").contains(r.roundStatus())
        || r.playerResults().size() < 2
        || r.playerResults().stream()
                .map(PlayerResult::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .signum()
            != 0) throw new BUSException(ErrorCode.INVALID_REQUEST, "Kết quả không hợp lệ");
    try (var c = connections.open()) {
      c.setAutoCommit(false);
      try {
        // Lock users theo ID tăng dần, tránh deadlock giữa các transaction.
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        for (var p :
            r.playerResults().stream()
                .sorted(Comparator.comparingLong(PlayerResult::userId))
                .toList()) {
          try (var s = c.prepareStatement("SELECT balance FROM users WHERE id=? FOR UPDATE")) {
            s.setLong(1, p.userId());
            try (var rs = s.executeQuery()) {
              if (!rs.next()) throw new SQLException("Thiếu người chơi");
              balances.put(p.userId(), rs.getBigDecimal(1));
            }
          }
        }
        // Sau khi lock người tham gia, kiểm tra lại chống ghi lặp.
        try (var s =
            c.prepareStatement(
                "SELECT status,ended_at,content_hash FROM play_rounds WHERE round_id=?")) {
          s.setString(1, r.roundId());
          try (var rs = s.executeQuery()) {
            if (rs.next()) {
              if (!hash.equals(rs.getString(3))
                  || !r.roundStatus().equals(rs.getString(1))
                  || r.endedAtMillis() != rs.getLong(2))
                throw new SQLException("roundId trùng nhưng khác nội dung");
              verifyDuplicate(c, r);
              c.rollback();
              return;
            }
          }
        }
        update(
            c,
            "INSERT INTO"
                + " play_rounds(round_id,content_hash,runtime_room_id,room_name,ante,status,ended_at)"
                + " VALUES(?,?,?,?,?,?,?)",
            r.roundId(),
            hash,
            completed.roomId(),
            completed.roomName(),
            completed.ante(),
            r.roundStatus(),
            r.endedAtMillis());
        var eval = new HandEvaluator();
        for (var p : r.playerResults()) {
          var hand =
              completed.hands().stream()
                  .filter(h -> h.userId() == p.userId())
                  .findFirst()
                  .orElseThrow();
          var evaluated = eval.evaluate(hand.cards());
          update(
              c,
              "INSERT INTO"
                  + " play_results(round_id,user_id,display_name,placement,outcome,profit,hand_type,score)"
                  + " VALUES(?,?,?,?,?,?,?,?)",
              r.roundId(),
              p.userId(),
              p.displayName(),
              p.rank(),
              p.result(),
              p.profit(),
              evaluated.handType().name(),
              evaluated.score());
          for (int i = 0; i < hand.cards().size(); i++) {
            var card = hand.cards().get(i);
            update(
                c,
                "INSERT INTO play_cards(round_id,user_id,position_no,rank_name,suit_name)"
                    + " VALUES(?,?,?,?,?)",
                r.roundId(),
                p.userId(),
                i + 1,
                card.rank().name(),
                card.suit().name());
          }
          if ("FINISHED".equals(r.roundStatus())) {
            BigDecimal before = balances.get(p.userId()), after = before.add(p.profit());
            if (after.signum() < 0) throw new SQLException("Số dư không đủ để chốt ván");
            update(
                c,
                "INSERT INTO play_ledger(round_id,user_id,delta,balance_before,balance_after)"
                    + " VALUES(?,?,?,?,?)",
                r.roundId(),
                p.userId(),
                p.profit(),
                before,
                after);
            update(
                c,
                "UPDATE users SET"
                    + " balance=?,total_games=total_games+1,total_wins=total_wins+?,total_losses=total_losses+?"
                    + " WHERE id=?",
                after,
                "WIN".equals(p.result()) ? 1 : 0,
                Set.of("LOSE", "FOLD").contains(p.result()) ? 1 : 0,
                p.userId());
          }
        }
        for (var b : r.betHistory())
          update(
              c,
              "INSERT INTO"
                  + " play_bets(round_id,sequence_no,user_id,action,total_bet,added_amount,created_at)"
                  + " VALUES(?,?,?,?,?,?,?)",
              r.roundId(),
              b.sequenceNo(),
              b.userId(),
              b.action().name(),
              b.raisedPoint(),
              b.raiseAmount(),
              b.createdAtMillis());
        for (int i = 0; i < r.sidePots().size(); i++) {
          var pot = r.sidePots().get(i);
          update(
              c,
              "INSERT INTO play_pots(round_id,pot_no,amount) VALUES(?,?,?)",
              r.roundId(),
              i + 1,
              pot.amount());
          for (long id : pot.eligiblePlayerIds())
            update(
                c,
                "INSERT INTO play_pot_players(round_id,pot_no,user_id) VALUES(?,?,?)",
                r.roundId(),
                i + 1,
                id);
        }
        c.commit();
      } catch (Exception e) {
        try {
          c.rollback();
        } catch (SQLException rollback) {
          e.addSuppressed(rollback);
        }
        throw e;
      }
    } catch (Exception e) {
      throw new BUSException(
          ErrorCode.INTERNAL_ERROR, "Chưa lưu được kết quả; server sẽ thử lại", e);
    }
  }

  private static String fingerprint(CompletedRound completed) throws BUSException {
    try {
      var bytes = new java.io.ByteArrayOutputStream();
      try (var out = new java.io.ObjectOutputStream(bytes)) {
        out.writeObject(completed);
      }
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    } catch (Exception e) {
      throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không tạo được định danh kết quả", e);
    }
  }

  private void verifyDuplicate(Connection c, RoundResult r) throws SQLException {
    try (var s =
        c.prepareStatement("SELECT user_id,outcome,profit FROM play_results WHERE round_id=?")) {
      s.setString(1, r.roundId());
      int count = 0;
      try (var rows = s.executeQuery()) {
        while (rows.next()) {
          count++;
          var p =
              r.playerResults().stream()
                  .filter(
                      x -> {
                        try {
                          return x.userId() == rows.getLong(1);
                        } catch (SQLException e) {
                          throw new IllegalStateException(e);
                        }
                      })
                  .findFirst()
                  .orElseThrow();
          if (!p.result().equals(rows.getString(2))
              || p.profit().compareTo(rows.getBigDecimal(3)) != 0)
            throw new SQLException("Kết quả ghi lặp khác nội dung");
        }
      }
      if (count != r.playerResults().size())
        throw new SQLException("Kết quả ghi lặp thiếu người chơi");
    }
  }

  private static void update(Connection c, String sql, Object... args) throws SQLException {
    try (var s = c.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
      s.executeUpdate();
    }
  }

  private static void page(int p, int size) throws BUSException {
    if (p < 0 || p > 1_000_000 || size < 1 || size > 100)
      throw new BUSException(ErrorCode.INVALID_PAGE, "Trang >=0, kích thước 1–100");
  }

  @Override
  public PageResult<GameHistoryItem> getHistory(long uid, int page, int size) throws BUSException {
    page(page, size);
    try (var c = connections.open()) {
      long total;
      try (var s = c.prepareStatement("SELECT COUNT(*) FROM play_results WHERE user_id=?")) {
        s.setLong(1, uid);
        try (var rs = s.executeQuery()) {
          rs.next();
          total = rs.getLong(1);
        }
      }
      var items = new ArrayList<GameHistoryItem>();
      try (var s =
          c.prepareStatement(
              "SELECT r.id,r.round_id,r.room_name,p.outcome,p.profit,p.hand_type,r.ended_at,(SELECT"
                  + " COUNT(*) FROM play_results x WHERE x.round_id=r.round_id) AS players FROM"
                  + " play_results p JOIN play_rounds r ON r.round_id=p.round_id WHERE p.user_id=?"
                  + " ORDER BY r.ended_at DESC,r.id DESC LIMIT ? OFFSET ?")) {
        s.setLong(1, uid);
        s.setInt(2, size);
        s.setLong(3, (long) page * size);
        try (var rs = s.executeQuery()) {
          while (rs.next())
            items.add(
                new GameHistoryItem(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getBigDecimal(5),
                    rs.getString(6),
                    rs.getLong(7),
                    rs.getInt(8)));
        }
      }
      return new PageResult<>(
          List.copyOf(items), page, size, total, PageResult.calcTotalPages(total, size));
    } catch (SQLException e) {
      throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không tải được lịch sử", e);
    }
  }

  @Override
  public PageResult<LeaderboardItem> getLeaderboard(int page, int size) throws BUSException {
    page(page, size);
    try (var c = connections.open()) {
      long total;
      try (var s = c.createStatement();
          var rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
        rs.next();
        total = rs.getLong(1);
      }
      var items = new ArrayList<LeaderboardItem>();
      try (var s =
          c.prepareStatement(
              "SELECT u.id,u.display_name,COALESCE(t.points,0) AS"
                  + " points,u.total_wins,u.total_games,u.total_losses FROM users u LEFT JOIN"
                  + " (SELECT user_id,SUM(CASE WHEN profit>0 THEN profit ELSE 0 END) AS points FROM"
                  + " play_results WHERE outcome<>'CANCELLED' GROUP BY user_id) t ON t.user_id=u.id"
                  + " ORDER BY points DESC,u.total_wins DESC,u.id ASC LIMIT ? OFFSET ?")) {
        s.setInt(1, size);
        s.setLong(2, (long) page * size);
        try (var rs = s.executeQuery()) {
          while (rs.next())
            items.add(
                new LeaderboardItem(
                    page * size + items.size() + 1,
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getBigDecimal(3),
                    rs.getInt(4),
                    rs.getInt(5),
                    rs.getInt(6)));
        }
      }
      return new PageResult<>(
          List.copyOf(items), page, size, total, PageResult.calcTotalPages(total, size));
    } catch (SQLException e) {
      throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không tải được bảng xếp hạng", e);
    }
  }
}
