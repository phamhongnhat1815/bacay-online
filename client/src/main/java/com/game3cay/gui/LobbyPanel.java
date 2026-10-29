package com.game3cay.gui;

import com.game3cay.shared.model.RoomSummary;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongConsumer;

public final class LobbyPanel extends JPanel {
    private final TableCard[] tables = new TableCard[20];
    private final JLabel summaryLabel = Theme.label("Đang tải danh sách bàn chơi…", 13, Theme.MUTED);
    private final Theme.Button quickPlayButton = new Theme.Button("Chơi ngay →", "gold");
    private final JTextField search = new Theme.HintField("Tìm số bàn…");
    private final Theme.Button[] filters = {new Theme.Button("Tất cả", "plain"),
            new Theme.Button("Có người chờ", "plain"), new Theme.Button("Bàn trống", "plain")};
    private final TableGrid grid = new TableGrid();
    private final CardLayout resultCards = new CardLayout();
    private final JPanel results = Theme.transparent(resultCards);
    private final JLabel visibleCount = Theme.label("", 12, Theme.MUTED);
    private LongConsumer joinAction = id -> {};
    private boolean areActionsEnabled;
    private int filter;

    public LobbyPanel() {
        super(new BorderLayout(0, 18));
        setOpaque(false);
        setBorder(new EmptyBorder(24, 28, 16, 28));
        var heading = Theme.transparent(new BorderLayout(16, 0));
        var copy = Theme.transparent(new BorderLayout(0, 6));
        var title = Theme.label("Chọn bàn chơi", 28, Theme.TEXT);
        title.setFont(Theme.font(28, true));
        copy.add(title, BorderLayout.NORTH); copy.add(summaryLabel, BorderLayout.SOUTH);
        heading.add(copy, BorderLayout.CENTER);
        quickPlayButton.setPreferredSize(new Dimension(154, 44));
        quickPlayButton.setToolTipText("Vào bàn có nhiều người đang chờ nhất; chọn bàn trống nếu chưa có ai");
        quickPlayButton.addActionListener(e -> handleQuickPlay());
        var primary = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        primary.add(quickPlayButton); heading.add(primary, BorderLayout.EAST);
        var toolbar = Theme.transparent(new BorderLayout(16, 0));
        Theme.field(search); search.setPreferredSize(new Dimension(200, 40));
        toolbar.add(search, BorderLayout.WEST);
        var filterRow = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (int i = 0; i < filters.length; i++) {
            final int index = i;
            filters[i].setPreferredSize(new Dimension(i == 1 ? 130 : 108, 40));
            filters[i].addActionListener(e -> { filter = index; applyFilters(); });
            filterRow.add(filters[i]);
        }
        toolbar.add(filterRow, BorderLayout.CENTER); toolbar.add(visibleCount, BorderLayout.EAST);
        var top = Theme.transparent(new BorderLayout(0, 22));
        top.add(heading, BorderLayout.NORTH); top.add(toolbar, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        for (int i = 0; i < tables.length; i++) { tables[i] = new TableCard(i + 1); grid.add(tables[i]); }
        var scroll = new JScrollPane(grid);
        scroll.setOpaque(false); scroll.getViewport().setOpaque(false); scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        results.add(scroll, "TABLES");
        var empty = Theme.transparent(new GridBagLayout());
        var emptyCopy = Theme.transparent(new GridLayout(0, 1, 0, 12));
        var emptyTitle = Theme.label("Không có bàn phù hợp", 21, Theme.TEXT);
        emptyTitle.setHorizontalAlignment(SwingConstants.CENTER); emptyCopy.add(emptyTitle);
        emptyCopy.add(Theme.label("Thử số bàn khác hoặc hiển thị lại tất cả bàn.", 14, Theme.MUTED));
        var reset = new Theme.Button("Xóa bộ lọc", "plain");
        reset.addActionListener(e -> { filter = 0; search.setText(""); applyFilters(); });
        emptyCopy.add(reset); empty.add(emptyCopy); results.add(empty, "EMPTY");
        add(results, BorderLayout.CENTER);
        add(Theme.label("2–8 người / bàn  ·  Chọn bàn đang chờ để tham gia  ·  Chủ bàn chọn cược cơ bản", 12, Theme.MUTED), BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilters(); }
            public void removeUpdate(DocumentEvent e) { applyFilters(); }
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        applyFilters(); setActionsEnabled(false);
    }

    public void onJoin(LongConsumer action) { joinAction = action; }
    public void setRooms(List<RoomSummary> rooms) {
        Map<Long, RoomSummary> roomMap = new HashMap<>();
        if (rooms != null) for (RoomSummary room : rooms) roomMap.put(room.roomId(), room);
        int people = 0, occupied = 0, loaded = 0;
        for (int i = 0; i < tables.length; i++) {
            RoomSummary room = roomMap.get((long) i + 1); tables[i].data = room;
            if (room != null) { loaded++; people += room.playerCount(); if (room.playerCount() > 0) occupied++; }
            tables[i].sync();
        }
        summaryLabel.setText(loaded == 0 ? "Đang tải danh sách bàn chơi…"
                : people + " người đang ngồi bàn  ·  " + occupied + " bàn có người  ·  " + (loaded - occupied) + " bàn trống");
        applyFilters();
    }
    private void applyFilters() {
        String query = search.getText().toLowerCase(Locale.ROOT).replace("bàn", "").replace("#", "").trim();
        int count = 0;
        for (TableCard card : tables) {
            if (card == null) continue;
            RoomSummary room = card.data;
            boolean matches = String.format("%02d", card.number).contains(query)
                    && (filter == 0 || room != null && "WAITING".equals(room.status())
                    && (filter == 1 ? room.playerCount() > 0 && room.playerCount() < room.maxPlayers() : room.playerCount() == 0));
            card.setVisible(matches); if (matches) count++;
        }
        for (int i = 0; i < filters.length; i++) {
            filters[i].putClientProperty("active", i == filter);
            filters[i].getAccessibleContext().setAccessibleDescription(i == filter ? "Đang chọn" : "");
            filters[i].repaint();
        }
        visibleCount.setText(count + " / 20 bàn");
        resultCards.show(results, count == 0 ? "EMPTY" : "TABLES"); grid.revalidate(); grid.repaint();
    }
    public void setActionsEnabled(boolean enabled) {
        areActionsEnabled = enabled; quickPlayButton.setEnabled(enabled);
        for (TableCard table : tables) table.sync();
    }
    private void handleQuickPlay() {
        if (!areActionsEnabled) return;
        TableCard best = null;
        for (TableCard card : tables) {
            if (card.data != null && "WAITING".equals(card.data.status()) && card.data.playerCount() < card.data.maxPlayers()
                    && (best == null || card.data.playerCount() > best.data.playerCount())) best = card;
        }
        if (best != null) joinAction.accept(best.number);
        else JOptionPane.showMessageDialog(this, "Các bàn đang chơi hoặc đã đủ người. Vui lòng thử lại sau.",
                "Chưa có bàn trống", JOptionPane.INFORMATION_MESSAGE);
    }
    private static String compactBet(BigDecimal amount) {
        if (amount == null) return "—";
        for (int i = 3; i >= 1; i--) {
            BigDecimal unit = BigDecimal.TEN.pow(3 * i);
            if (amount.compareTo(unit) >= 0) return Theme.points(amount.divide(unit, 1, RoundingMode.DOWN)) + new String[]{"", "K", "M", " tỷ"}[i];
        }
        return Theme.points(amount);
    }
    /** Cards keep a readable minimum width and scroll vertically as the window narrows. */
    private static final class TableGrid extends JPanel implements Scrollable {
        private static final int GAP = 14, HEIGHT = 166;
        TableGrid() { super(null); setOpaque(false); }
        private int columns(int width) { return Math.max(1, Math.min(5, (width + GAP) / (224 + GAP))); }
        private int visibleCards() { int n = 0; for (Component c : getComponents()) if (c.isVisible()) n++; return n; }
        @Override public Dimension getPreferredSize() {
            int width = getParent() == null ? 1000 : getParent().getWidth();
            int rows = (visibleCards() + columns(width) - 1) / columns(width);
            return new Dimension(width, Math.max(0, rows * (HEIGHT + GAP) - GAP));
        }
        @Override public void doLayout() {
            int cols = columns(getWidth()), i = 0, width = (getWidth() - (cols - 1) * GAP) / cols;
            for (Component c : getComponents()) if (c.isVisible()) {
                c.setBounds((i % cols) * (width + GAP), (i / cols) * (HEIGHT + GAP), width, HEIGHT); i++;
            }
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(1000, 526); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private final class TableCard extends JButton {
        final int number; RoomSummary data;
        TableCard(int number) {
            this.number = number;
            setOpaque(false); setBorderPainted(false); setContentAreaFilled(false); setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName("Bàn " + number);
            addActionListener(e -> { Sound.play("button_click"); joinAction.accept(number); });
        }
        private boolean joinable() { return data != null && "WAITING".equals(data.status()) && data.playerCount() < data.maxPlayers(); }
        private String stateLabel() {
            if (data == null) return "Đang tải";
            if ("PLAYING".equals(data.status())) return "Đang chơi";
            if ("RESULT".equals(data.status())) return "Kết quả";
            if (data.playerCount() >= data.maxPlayers()) return "Đã đầy";
            return data.playerCount() == 0 ? "Bàn trống" : "Đang chờ";
        }
        void sync() {
            setEnabled(areActionsEnabled && joinable());
            String detail = "Bàn " + number + " · " + stateLabel() + (data == null ? ""
                    : " · " + data.playerCount() + "/" + data.maxPlayers() + " người · " + Theme.points(data.betAmount()) + " điểm / người");
            setToolTipText(detail); getAccessibleContext().setAccessibleDescription(detail); repaint();
        }
        @Override protected void paintComponent(Graphics gg) {
            Graphics2D g = (Graphics2D) gg.create(); Assets.quality(g);
            int w = getWidth(), h = getHeight(); boolean hover = isEnabled() && getModel().isRollover();
            g.setColor(hover ? Theme.SURFACE : Theme.PANEL); g.fillRoundRect(0, 0, w, h, 18, 18);
            g.setColor(hover || hasFocus() ? Theme.GOLD : Theme.LINE);
            g.setStroke(new BasicStroke(hover || hasFocus() ? 2f : 1f)); g.drawRoundRect(1, 1, w - 3, h - 3, 18, 18);
            g.setFont(Theme.font(20, true)); g.setColor(Theme.TEXT); g.drawString("Bàn " + String.format("%02d", number), 18, 33);
            String state = stateLabel(); Color stateColor = data != null && data.playerCount() > 0 && joinable() ? Theme.GREEN : Theme.MUTED;
            g.setFont(Theme.font(11, true)); int sw = g.getFontMetrics().stringWidth(state);
            g.setColor(new Color(stateColor.getRed(), stateColor.getGreen(), stateColor.getBlue(), 22));
            g.fillRoundRect(w - sw - 30, 14, sw + 16, 25, 10, 10); g.setColor(stateColor); g.drawString(state, w - sw - 22, 31);
            int count = data == null ? 0 : data.playerCount(), max = data == null ? 8 : data.maxPlayers();
            g.setFont(Theme.font(25, true)); g.setColor(count > 0 ? Theme.TEXT : Theme.MUTED); g.drawString(String.valueOf(count), 18, 79);
            g.setFont(Theme.font(12, false)); g.setColor(Theme.MUTED); g.drawString("/ " + max + " người", 40, 79);
            for (int i = 0; i < max; i++) { g.setColor(i < count ? Theme.GREEN : Theme.LINE); g.fillRoundRect(18 + i * 14, 92, 9, 6, 4, 4); }
            g.setColor(new Color(11, 24, 37)); g.fillOval(w - 100, 53, 82, 44);
            g.setColor(Theme.LINE); g.drawOval(w - 100, 53, 82, 44);
            g.setColor(new Color(36, 69, 78)); g.fillOval(w - 93, 60, 68, 30);
            g.setColor(new Color(160, 182, 188));
            for (int i = 0; i < 3; i++) g.fillRoundRect(w - 75 + i * 12, 67, 9, 15, 2, 2);
            g.setColor(Theme.LINE); g.drawLine(18, h - 48, w - 18, h - 48);
            g.setFont(Theme.font(13, true)); g.setColor(Theme.GOLD); g.drawString(data == null ? "—" : compactBet(data.betAmount()) + " điểm", 18, h - 19);
            String action = data == null ? "Đang tải…" : joinable() ? "Vào bàn →" : "Chờ ván sau";
            g.setFont(Theme.font(12, true)); g.setColor(isEnabled() ? Theme.TEXT : Theme.MUTED);
            g.drawString(action, w - 18 - g.getFontMetrics().stringWidth(action), h - 19); g.dispose();
        }
    }
}
