package com.game3cay.admin.gui;

import com.game3cay.shared.model.AdminBroadcastRequest;
import com.game3cay.shared.model.AdminStatsSnapshot;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.Serial;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Tab Tổng Quan (Dashboard) hiển thị số liệu thời gian thực và công cụ phát thông báo hệ thống.
 */
public final class DashboardPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    private final JLabel onlineUsersLabel = AdminTheme.label("0", 24, AdminTheme.GREEN, true);
    private final JLabel activeRoomsLabel = AdminTheme.label("0", 24, AdminTheme.GOLD, true);
    private final JLabel totalUsersLabel = AdminTheme.label("0", 24, AdminTheme.TEXT, true);
    private final JLabel totalBalanceLabel = AdminTheme.label("0", 22, AdminTheme.GOLD, true);
    private final JLabel todayRoundsLabel = AdminTheme.label("0", 24, AdminTheme.TEXT, true);

    private final JTextField broadcastMessageField = new JTextField();
    private final JComboBox<String> broadcastLevelCombo = new JComboBox<>(new String[] {"INFO", "WARNING", "URGENT"});
    private final AdminTheme.Button broadcastButton = new AdminTheme.Button("GỬI THÔNG BÁO TẤT CẢ", "gold");
    private final JLabel broadcastStatusLabel = AdminTheme.label(" ", 12, AdminTheme.MUTED);

    private final AdminTheme.Button refreshButton = new AdminTheme.Button("LÀM MỚI SỐ LIỆU", "plain");
    private final JLabel lastUpdatedLabel = AdminTheme.label("Chưa cập nhật", 12, AdminTheme.MUTED);

    private Consumer<AdminBroadcastRequest> onBroadcastCallback;
    private Runnable onRefreshCallback;

    public DashboardPanel() {
        super(new BorderLayout(0, 20));
        setBackground(AdminTheme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        // Top Row: Title + Refresh
        JPanel topBar = AdminTheme.transparent(new BorderLayout());
        JPanel titlePanel = AdminTheme.transparent(new GridLayout(0, 1, 0, 2));
        titlePanel.add(AdminTheme.label("TỔNG QUAN HỆ THỐNG", 20, AdminTheme.GOLD, true));
        titlePanel.add(AdminTheme.label("Theo dõi số liệu lưu hành và hoạt động máy chủ theo thời gian thực", 12, AdminTheme.MUTED));
        topBar.add(titlePanel, BorderLayout.WEST);

        JPanel refreshBar = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        refreshBar.add(lastUpdatedLabel);
        refreshBar.add(refreshButton);
        topBar.add(refreshBar, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // Center Area
        JPanel center = AdminTheme.transparent(new BorderLayout(0, 20));

        // 5 KPI Cards Grid
        JPanel cardsGrid = AdminTheme.transparent(new GridLayout(1, 5, 14, 0));
        cardsGrid.add(AdminTheme.card("Trực Tuyến", onlineUsersLabel, "Người chơi online"));
        cardsGrid.add(AdminTheme.card("Bàn Hoạt Động", activeRoomsLabel, "Bàn đang có người / chơi"));
        cardsGrid.add(AdminTheme.card("Tổng Tài Khoản", totalUsersLabel, "Đã đăng ký trong CSDL"));
        cardsGrid.add(AdminTheme.card("Tổng Điểm Lưu Hành", totalBalanceLabel, "Tổng số dư toàn server"));
        cardsGrid.add(AdminTheme.card("Số Ván Hôm Nay", todayRoundsLabel, "Ván đã hoàn tất từ 0h"));
        center.add(cardsGrid, BorderLayout.NORTH);

        // Content Area below cards
        JPanel contentArea = AdminTheme.transparent(new BorderLayout(0, 16));

        // 1. Broadcast Card Panel
        JPanel broadcastCard = new JPanel(new BorderLayout(0, 14)) {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AdminTheme.PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(AdminTheme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        broadcastCard.setOpaque(false);
        broadcastCard.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel broadcastHeader = AdminTheme.transparent(new GridLayout(0, 1, 0, 2));
        broadcastHeader.add(AdminTheme.label("PHÁT THÔNG BÁO TOÀN HỆ THỐNG", 15, AdminTheme.GOLD, true));
        broadcastHeader.add(AdminTheme.label("Thông báo sẽ được gửi trực tiếp tới tất cả người chơi và hiển thị nổi bật trên bàn chơi", 12, AdminTheme.MUTED));
        broadcastCard.add(broadcastHeader, BorderLayout.NORTH);

        JPanel broadcastControls = AdminTheme.transparent(new BorderLayout(10, 0));
        broadcastControls.setPreferredSize(new Dimension(800, 40));
        broadcastControls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        AdminTheme.field(broadcastMessageField);
        broadcastMessageField.setPreferredSize(new Dimension(500, 40));

        broadcastLevelCombo.setFont(AdminTheme.font(13, false));
        broadcastLevelCombo.setBackground(AdminTheme.PANEL_ALT);
        broadcastLevelCombo.setForeground(AdminTheme.TEXT);
        broadcastLevelCombo.setPreferredSize(new Dimension(120, 40));

        broadcastControls.add(broadcastLevelCombo, BorderLayout.WEST);
        broadcastControls.add(broadcastMessageField, BorderLayout.CENTER);
        broadcastControls.add(broadcastButton, BorderLayout.EAST);

        JPanel broadcastFooter = AdminTheme.transparent(new BorderLayout());
        broadcastFooter.setBorder(new EmptyBorder(6, 4, 0, 0));
        broadcastFooter.add(broadcastStatusLabel, BorderLayout.WEST);

        JPanel broadcastBody = AdminTheme.transparent(new BorderLayout(0, 0));
        broadcastBody.add(broadcastControls, BorderLayout.CENTER);
        broadcastBody.add(broadcastFooter, BorderLayout.SOUTH);

        broadcastCard.add(broadcastBody, BorderLayout.CENTER);
        contentArea.add(broadcastCard, BorderLayout.NORTH);

        // 2. Server Architecture & Health Card
        JPanel infoCard = new JPanel(new BorderLayout(0, 14)) {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AdminTheme.PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(AdminTheme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        infoCard.setOpaque(false);
        infoCard.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel infoHeader = AdminTheme.transparent(new GridLayout(0, 1, 0, 2));
        infoHeader.add(AdminTheme.label("THÔNG SỐ MÁY CHỦ & KIẾN TRÚC HỆ THỐNG", 15, AdminTheme.GOLD, true));
        infoHeader.add(AdminTheme.label("Cấu hình hạ tầng mạng socket nhị phân Packet v2 và an toàn cơ sở dữ liệu", 12, AdminTheme.MUTED));
        infoCard.add(infoHeader, BorderLayout.NORTH);

        JPanel infoGrid = AdminTheme.transparent(new GridLayout(2, 2, 14, 10));
        infoGrid.add(createTile("CỔNG DỊCH VỤ TCP", "0.0.0.0 : 8888 (Tối đa 256 kết nối socket đồng thời)"));
        infoGrid.add(createTile("CHUẨN GIAO THỨC", "Custom Binary Packet v2 (Magic 0x3C33, Big-Endian, 8B Header)"));
        infoGrid.add(createTile("CƠ SỞ DỮ LIỆU", "MySQL 8.0 Community Server (InnoDB, Zero Deadlock Locking)"));
        infoGrid.add(createTile("BẢO MẬT & PHÂN QUYỀN", "Java ObjectInputFilter Whitelist + PBKDF2 (600.000 vòng lặp)"));
        infoCard.add(infoGrid, BorderLayout.CENTER);

        contentArea.add(infoCard, BorderLayout.CENTER);
        center.add(contentArea, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        // Actions
        refreshButton.addActionListener(e -> {
            if (onRefreshCallback != null) {
                onRefreshCallback.run();
            }
        });

        broadcastButton.addActionListener(e -> triggerBroadcast());
        broadcastMessageField.addActionListener(e -> triggerBroadcast());
    }

    private JPanel createTile(String title, String desc) {
        JPanel tile = new JPanel(new BorderLayout(0, 4)) {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AdminTheme.PANEL_ALT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(AdminTheme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        tile.setOpaque(false);
        tile.setBorder(new EmptyBorder(10, 14, 10, 14));
        tile.add(AdminTheme.label(title, 11, AdminTheme.GOLD, true), BorderLayout.NORTH);
        tile.add(AdminTheme.label(desc, 12, AdminTheme.TEXT), BorderLayout.CENTER);
        return tile;
    }

    private void triggerBroadcast() {
        String msg = broadcastMessageField.getText().trim();
        if (msg.isBlank()) {
            broadcastStatusLabel.setText("Vui lòng nhập nội dung thông báo trước khi phát.");
            broadcastStatusLabel.setForeground(AdminTheme.RED);
            return;
        }

        String level = (String) broadcastLevelCombo.getSelectedItem();
        if (onBroadcastCallback != null) {
            broadcastButton.setEnabled(false);
            broadcastStatusLabel.setText("Đang phát thông báo…");
            broadcastStatusLabel.setForeground(AdminTheme.MUTED);

            onBroadcastCallback.accept(new AdminBroadcastRequest(msg, level));
        }
    }

    public void updateStats(AdminStatsSnapshot stats) {
        if (stats == null) {
            return;
        }
        onlineUsersLabel.setText(String.valueOf(stats.onlineUsers()));
        activeRoomsLabel.setText(String.valueOf(stats.activeRooms()));
        totalUsersLabel.setText(String.valueOf(stats.totalRegisteredUsers()));
        totalBalanceLabel.setText(AdminTheme.points(stats.totalBalanceInCirculation()));
        todayRoundsLabel.setText(String.valueOf(stats.totalRoundsPlayedToday()));

        lastUpdatedLabel.setText("Cập nhật lúc: " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    public void broadcastSentSuccess() {
        broadcastButton.setEnabled(true);
        broadcastMessageField.setText("");
        broadcastStatusLabel.setText("✓ Đã phát thông báo thành công tới toàn bộ người chơi trực tuyến.");
        broadcastStatusLabel.setForeground(AdminTheme.GREEN);
    }

    public void broadcastFailed(String reason) {
        broadcastButton.setEnabled(true);
        broadcastStatusLabel.setText("Gửi thông báo thất bại: " + reason);
        broadcastStatusLabel.setForeground(AdminTheme.RED);
    }

    public void setOnRefresh(Runnable callback) {
        this.onRefreshCallback = callback;
    }

    public void setOnBroadcast(Consumer<AdminBroadcastRequest> callback) {
        this.onBroadcastCallback = callback;
    }
}
