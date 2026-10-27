package com.game3cay.gui;

import com.game3cay.shared.model.GameHistoryItem;
import com.game3cay.shared.model.HandType;
import com.game3cay.shared.model.LeaderboardItem;
import com.game3cay.shared.model.PageResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.Serial;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * DataDialog provides a modal or modeless data viewer dialog with pagination controls,
 * used for inspecting personal Game History or the server-wide Leaderboard.
 */
public final class DataDialog extends JDialog {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final DefaultTableModel rows = new DefaultTableModel() {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };

    private final JLabel status = Theme.label("Đang tải…", 13, Theme.MUTED);
    private final JLabel description = Theme.label("", 13, Theme.MUTED);
    private final Theme.Button previous = new Theme.Button("← Trước", "plain");
    private final Theme.Button next = new Theme.Button("Sau →", "plain");
    private final Theme.Button refresh = new Theme.Button("Làm mới", "gold");

    private int page;
    private int totalPages;
    private boolean loading = true;
    private IntConsumer loader;

    public DataDialog(JFrame owner, String title) {
        super(owner, title, false);

        var panel = new JPanel(new BorderLayout(0, 18));
        panel.setBackground(Theme.BG);
        panel.setBorder(new EmptyBorder(25, 27, 22, 27));

        var head = Theme.transparent(new GridLayout(0, 1, 0, 7));
        var heading = Theme.label(title, 27, Theme.GOLD);
        heading.setFont(Theme.font(27, true));
        head.add(heading);
        head.add(description);
        panel.add(head, BorderLayout.NORTH);

        var table = new PlainTable(rows);
        table.setRowHeight(39);
        table.setBackground(Theme.PANEL);
        table.setForeground(Theme.TEXT);
        table.setFont(Theme.font(14, false));
        table.setGridColor(new Color(25, 42, 68));
        table.setSelectionBackground(new Color(24, 48, 82));
        table.setSelectionForeground(Theme.GOLD);
        table.setShowVerticalLines(false);
        table.getTableHeader().setBackground(new Color(18, 34, 58));
        table.getTableHeader().setForeground(Theme.GOLD);
        table.getTableHeader().setFont(Theme.font(12, true));
        table.getTableHeader().setPreferredSize(new Dimension(200, 38));
        table.getTableHeader().setReorderingAllowed(false);

        var scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Theme.PANEL);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(45, 68, 100)));
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
        this.loader = action;
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
        this.loading = false;
        status.setText(message);
        buttons();
    }

    public void showPage(PageResult<?> result, boolean history) {
        this.page = result.page();
        this.totalPages = result.totalPages();
        this.loading = false;
        description.setText(history
                ? "Các ván đã chơi và điểm thay đổi của bạn."
                : "Xếp theo tổng điểm thắng trong các ván đã chơi.");

        rows.setColumnIdentifiers(history
                ? new String[] {"Thời gian", "Bàn", "Kết quả", "Bài", "Điểm +/−", "Người chơi"}
                : new String[] {"Hạng", "Người chơi", "Điểm thắng", "Thắng", "Đã chơi", "Thua"});
        rows.setRowCount(0);

        for (var item : result.items()) {
            if (item instanceof GameHistoryItem g) {
                rows.addRow(new Object[] {
                    TIME_FORMATTER.format(Instant.ofEpochMilli(g.playedAt())),
                    g.roomName(),
                    outcome(g.result()),
                    hand(g.handType()),
                    (g.profit().signum() > 0 ? "+" : "") + Theme.points(g.profit()),
                    g.playerCount()
                });
            } else if (item instanceof LeaderboardItem l) {
                rows.addRow(new Object[] {
                    l.rank(),
                    l.displayName(),
                    Theme.points(l.totalPoints()),
                    l.totalWins(),
                    l.totalGames(),
                    l.totalLosses()
                });
            }
        }

        status.setText(result.totalItems() == 0
                ? "Chưa có ván nào"
                : (page + 1) + " / " + totalPages + " trang · " + result.totalItems() + " kết quả");
        buttons();
    }

    private static String outcome(String s) {
        if (s == null) {
            return "—";
        }
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
        if (s == null) {
            return "—";
        }
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
