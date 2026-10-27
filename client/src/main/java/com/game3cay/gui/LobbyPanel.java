package com.game3cay.gui;

import com.game3cay.shared.model.RoomSummary;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

public final class LobbyPanel extends JPanel {
    private final TableCard[] tables = new TableCard[20];
    private final JLabel summaryLabel = Theme.label("Đang tải danh sách bàn chơi…", 14, Theme.MUTED);
    private final Theme.Button quickPlayButton = new Theme.Button("⚡ CHƠI NGAY", "gold");
    private LongConsumer joinAction = id -> {};
    private boolean areActionsEnabled;

    public LobbyPanel() {
        super(new BorderLayout(0, 18));
        setOpaque(false);
        setBorder(new EmptyBorder(24, 32, 18, 32));

        var titlePanel = Theme.transparent(new BorderLayout());
        var copyPanel = Theme.transparent(new GridLayout(0, 1, 0, 7));

        var eyebrow = Theme.label("SẢNH CHƠI / 20 BÀN", 11, Theme.GOLD);
        eyebrow.setFont(Theme.font(11, true));

        var headerTitle = Theme.label("Chọn bàn của bạn", 28, Theme.TEXT);
        headerTitle.setFont(Theme.font(28, true));

        copyPanel.add(eyebrow);
        copyPanel.add(headerTitle);
        copyPanel.add(summaryLabel);
        titlePanel.add(copyPanel, BorderLayout.WEST);

        var eastPanel = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        var noteLabel = Theme.label("2–8 người / bàn  •  Chủ bàn chọn cược", 12, Theme.MUTED);
        eastPanel.add(noteLabel);

        quickPlayButton.setPreferredSize(new Dimension(140, 42));
        quickPlayButton.setFont(Theme.font(13, true));
        quickPlayButton.setToolTipText("Tự động tìm và tham gia bàn đang có người chờ thích hợp nhất");
        quickPlayButton.addActionListener(e -> handleQuickPlay());
        eastPanel.add(quickPlayButton);

        titlePanel.add(eastPanel, BorderLayout.EAST);
        add(titlePanel, BorderLayout.NORTH);

        var grid = new TableGrid();
        for (int i = 0; i < 20; i++) {
            tables[i] = new TableCard(i + 1);
            grid.add(tables[i]);
        }

        var scrollPane = new JScrollPane(grid);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        add(scrollPane, BorderLayout.CENTER);

        var footerPanel = Theme.transparent(new BorderLayout());
        footerPanel.add(
                Theme.label("Chọn một bàn trống để mở cuộc chơi, hoặc vào bàn đang chờ để chơi cùng mọi người.", 13, Theme.MUTED),
                BorderLayout.WEST
        );
        footerPanel.add(Theme.label("● Có người    ○ Còn trống", 12, Theme.GOLD), BorderLayout.EAST);
        add(footerPanel, BorderLayout.SOUTH);
    }

    public void onJoin(LongConsumer action) {
        this.joinAction = action;
    }

    public void setRooms(List<RoomSummary> rooms) {
        Map<Long, RoomSummary> roomMap = new HashMap<>();
        if (rooms != null) {
            for (RoomSummary r : rooms) {
                roomMap.put(r.roomId(), r);
            }
        }

        int occupiedCount = 0;
        int totalPeople = 0;

        for (int i = 0; i < 20; i++) {
            RoomSummary summary = roomMap.get((long) (i + 1));
            tables[i].data = summary;
            if (summary != null) {
                totalPeople += summary.playerCount();
                if (summary.playerCount() > 0) {
                    occupiedCount++;
                }
            }
            tables[i].sync();
        }

        summaryLabel.setText(
                totalPeople + " người đang ngồi bàn  ·  "
                        + occupiedCount + " bàn có người  ·  "
                        + (20 - occupiedCount) + " bàn trống"
        );
    }

    public void setActionsEnabled(boolean enabled) {
        this.areActionsEnabled = enabled;
        quickPlayButton.setEnabled(enabled);
        for (TableCard table : tables) {
            table.sync();
        }
    }

    private void handleQuickPlay() {
        if (!areActionsEnabled) {
            return;
        }
        TableCard bestCard = null;
        int maxOccupants = -1;

        for (TableCard tc : tables) {
            if (tc.data != null && "WAITING".equals(tc.data.status()) && tc.data.playerCount() < tc.data.maxPlayers()) {
                if (tc.data.playerCount() > maxOccupants && tc.data.playerCount() > 0) {
                    maxOccupants = tc.data.playerCount();
                    bestCard = tc;
                }
            }
        }

        if (bestCard == null) {
            for (TableCard tc : tables) {
                if (tc.data != null && "WAITING".equals(tc.data.status()) && tc.data.playerCount() == 0) {
                    bestCard = tc;
                    break;
                }
            }
        }

        if (bestCard != null) {
            Sound.play("button_click");
            joinAction.accept(bestCard.number);
        } else {
            javax.swing.JOptionPane.showMessageDialog(
                    this,
                    "Hiện tại tất cả các bàn đều đang diễn ra ván bài hoặc đã đầy chỗ.\nVui lòng thử lại sau giây lát.",
                    "Thông báo",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    private static String compactBet(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        BigDecimal thousand = new BigDecimal("1000");
        BigDecimal million = new BigDecimal("1000000");
        BigDecimal billion = new BigDecimal("1000000000");

        if (amount.compareTo(billion) >= 0) {
            return Theme.points(amount.divide(billion, 1, RoundingMode.DOWN)) + " tỷ điểm";
        }
        if (amount.compareTo(million) >= 0) {
            return Theme.points(amount.divide(million, 1, RoundingMode.DOWN)) + "M điểm";
        }
        if (amount.compareTo(thousand) >= 0) {
            return Theme.points(amount.divide(thousand, 1, RoundingMode.DOWN)) + "K điểm";
        }
        return Theme.points(amount) + " điểm";
    }

    private static final class TableGrid extends JPanel implements Scrollable {
        TableGrid() {
            super(new GridLayout(4, 5, 14, 14));
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1000, 514);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return r.height - 24;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= 514;
        }
    }

    private final class TableCard extends JButton {
        final int number;
        RoomSummary data;

        TableCard(int n) {
            this.number = n;
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Vào bàn " + n);
            getAccessibleContext().setAccessibleName("Bàn " + n);

            addActionListener(e -> {
                Sound.play("button_click");
                joinAction.accept(number);
            });
        }

        void sync() {
            boolean canJoin = areActionsEnabled
                    && data != null
                    && "WAITING".equals(data.status())
                    && data.playerCount() < data.maxPlayers();

            setEnabled(canJoin);
            setToolTipText(data == null
                    ? "Đang tải thông tin bàn " + number
                    : "Bàn " + number + " · Cược cơ bản " + Theme.points(data.betAmount()) + " điểm / người");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics gg) {
            Graphics2D g = (Graphics2D) gg.create();
            Assets.quality(g);
            int width = getWidth();
            int height = getHeight();
            boolean hover = getModel().isRollover() && isEnabled();

            g.setPaint(new GradientPaint(
                    0, 0, new Color(16, 32, 56, 245),
                    width, height, new Color(8, 18, 32, 250)
            ));
            g.fillRoundRect(0, 0, width, height, 17, 17);

            g.setColor(hover ? Theme.GOLD : new Color(55, 78, 110));
            g.setStroke(new BasicStroke(hover ? 2f : 1f));
            g.drawRoundRect(1, 1, width - 3, height - 3, 17, 17);

            int playerCount = (data == null) ? 0 : data.playerCount();
            boolean isPlaying = data != null && "PLAYING".equals(data.status());
            boolean isResult = data != null && "RESULT".equals(data.status());

            g.setFont(Theme.font(20, true));
            g.setColor(Theme.TEXT);
            g.drawString("Bàn " + String.format("%02d", number), 17, 31);

            g.setFont(Theme.font(11, true));
            String tag = isResult ? "KẾT QUẢ"
                    : isPlaying ? "ĐANG CHƠI"
                    : playerCount == 8 ? "ĐÃ ĐẦY"
                    : playerCount > 0 ? "CÓ NGƯỜI" : "";

            g.setColor((isPlaying || isResult) ? Theme.GOLD : new Color(110, 190, 240));
            g.drawString(tag, width - 15 - g.getFontMetrics().stringWidth(tag), 29);

            int tableWidth = Math.min(106, width / 2);
            int tableHeight = Math.min(65, height - 60);
            g.drawImage(Assets.get("table.png", 240), 14, 39, tableWidth, tableHeight, null);

            g.setFont(Theme.font(25, true));
            g.setColor(playerCount > 0 ? Theme.TEXT : Theme.MUTED);
            g.drawString(String.valueOf(playerCount), width - 63, 70);

            g.setFont(Theme.font(12, false));
            g.setColor(Theme.MUTED);
            g.drawString("/ 8", width - 43, 70);

            for (int i = 0; i < 8; i++) {
                g.setColor(i < playerCount ? Theme.GOLD : new Color(38, 58, 88));
                g.fillOval(width - 72 + (i % 4) * 12, 79 + (i / 4) * 12, 7, 7);
            }

            g.setColor(new Color(42, 65, 96));
            g.drawLine(16, height - 34, width - 16, height - 34);

            g.setFont(Theme.font(12, false));
            g.setColor(Theme.GOLD);
            g.drawString((data == null) ? "—" : compactBet(data.betAmount()), 17, height - 13);

            g.setColor(hover ? Theme.GOLD : Theme.MUTED);
            String actionLabel = (data == null) ? "Đang tải…"
                    : (isPlaying || isResult) ? "Chờ ván sau"
                    : (playerCount == 8) ? "Đủ người" : "Vào bàn →";

            g.drawString(actionLabel, width - 15 - g.getFontMetrics().stringWidth(actionLabel), height - 13);

            if (hasFocus()) {
                g.setColor(Theme.GOLD);
                g.drawRoundRect(4, 4, width - 9, height - 9, 14, 14);
            }
            g.dispose();
        }
    }
}
