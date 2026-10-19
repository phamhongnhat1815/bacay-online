package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.IntConsumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

public final class DataDialog extends JDialog {
  private final DefaultTableModel rows =
      new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final JLabel status = Theme.label("Đang tải…", 13, Theme.MUTED),
      description = Theme.label("", 13, Theme.MUTED);
  private final Theme.Button previous = new Theme.Button("← Trước", "plain"),
      next = new Theme.Button("Sau →", "plain"),
      refresh = new Theme.Button("Làm mới", "gold");
  private int page, totalPages;
  private boolean loading = true;
  private IntConsumer loader;

  public DataDialog(JFrame owner, String title) {
    super(owner, title, false);
    var panel = new JPanel(new BorderLayout(0, 18));
    panel.setBackground(Theme.BG);
    panel.setBorder(new EmptyBorder(25, 27, 22, 27));
    var head = Theme.transparent(new GridLayout(0, 1, 0, 7));
    var h = Theme.label(title, 27, Theme.GOLD);
    h.setFont(Theme.font(27, true));
    head.add(h);
    head.add(description);
    panel.add(head, BorderLayout.NORTH);
    var table = new PlainTable(rows);
    table.setRowHeight(39);
    table.setBackground(Theme.PANEL);
    table.setForeground(Theme.TEXT);
    table.setFont(Theme.font(14, false));
    table.setGridColor(new Color(42, 64, 52));
    table.setSelectionBackground(new Color(60, 80, 55));
    table.setSelectionForeground(Theme.GOLD);
    table.setShowVerticalLines(false);
    table.getTableHeader().setBackground(new Color(35, 57, 43));
    table.getTableHeader().setForeground(Theme.GOLD);
    table.getTableHeader().setFont(Theme.font(12, true));
    table.getTableHeader().setPreferredSize(new Dimension(200, 38));
    table.getTableHeader().setReorderingAllowed(false);
    var scroll = new JScrollPane(table);
    scroll.getViewport().setBackground(Theme.PANEL);
    scroll.setBorder(BorderFactory.createLineBorder(new Color(72, 86, 61)));
    panel.add(scroll, BorderLayout.CENTER);
    var bottom = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 12, 0));
    bottom.add(status);
    bottom.add(previous);
    bottom.add(next);
    bottom.add(refresh);
    panel.add(bottom, BorderLayout.SOUTH);
    previous.addActionListener(e -> load(page - 1));
    next.addActionListener(e -> load(page + 1));
    refresh.addActionListener(e -> load(page));
    setContentPane(panel);
    setSize(950, 600);
    setMinimumSize(new Dimension(800, 430));
    setLocationRelativeTo(owner);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    buttons();
  }

  public void onLoad(IntConsumer action) {
    loader = action;
  }

  private void load(int n) {
    if (!loading && loader != null) {
      loading = true;
      status.setText("Đang tải…");
      buttons();
      loader.accept(n);
    }
  }

  public void failure(String message) {
    loading = false;
    status.setText(message);
    buttons();
  }

  public void showPage(PageResult<?> result, boolean history) {
    page = result.page();
    totalPages = result.totalPages();
    loading = false;
    description.setText(
        history
            ? "Các ván đã chơi và điểm thay đổi của bạn."
            : "Xếp theo tổng điểm thắng trong các ván đã chơi.");
    rows.setColumnIdentifiers(
        history
            ? new String[] {"Thời gian", "Bàn", "Kết quả", "Bài", "Điểm +/−", "Người chơi"}
            : new String[] {"Hạng", "Người chơi", "Điểm thắng", "Thắng", "Đã chơi", "Thua"});
    rows.setRowCount(0);
    for (var item : result.items()) {
      if (item instanceof GameHistoryItem g)
        rows.addRow(
            new Object[] {
              DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                  .withZone(ZoneId.systemDefault())
                  .format(Instant.ofEpochMilli(g.playedAt())),
              g.roomName(),
              outcome(g.result()),
              hand(g.handType()),
              (g.profit().signum() > 0 ? "+" : "") + Theme.points(g.profit()),
              g.playerCount()
            });
      else if (item instanceof LeaderboardItem l)
        rows.addRow(
            new Object[] {
              l.rank(),
              l.displayName(),
              Theme.points(l.totalPoints()),
              l.totalWins(),
              l.totalGames(),
              l.totalLosses()
            });
    }
    status.setText(
        result.totalItems() == 0
            ? "Chưa có ván nào"
            : (page + 1) + " / " + totalPages + " trang · " + result.totalItems() + " kết quả");
    buttons();
  }

  private static String outcome(String s) {
    if (s == null) return "—";
    return switch (s) {
      case "WIN" -> "Thắng";
      case "LOSE" -> "Thua";
      case "DRAW" -> "Hòa";
      case "FOLD" -> "Úp bài";
      case "CANCELLED" -> "Hoàn điểm";
      default -> s;
    };
  }

  private static String hand(String s) {
    if (s == null) return "—";
    try {
      return HandType.valueOf(s).displayName();
    } catch (IllegalArgumentException e) {
      return s;
    }
  }

  private void buttons() {
    previous.setEnabled(!loading && page > 0);
    next.setEnabled(!loading && page + 1 < totalPages);
    refresh.setEnabled(!loading);
  }
}
