package com.game3cay.admin.gui;

import com.game3cay.shared.model.AdminBroadcastRequest;
import com.game3cay.shared.model.AdminStatsSnapshot;
import com.game3cay.shared.network.PacketIO;
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

        JPanel topBar = AdminTheme.transparent(new BorderLayout());
        JPanel titlePanel = AdminTheme.transparent(new GridLayout(0, 1, 0, 2));
        titlePanel.add(AdminTheme.label("TỔNG QUAN", 20, AdminTheme.GOLD, true));
        topBar.add(titlePanel, BorderLayout.WEST);

        JPanel refreshBar = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        refreshBar.add(lastUpdatedLabel);
        refreshBar.add(refreshButton);
        topBar.add(refreshBar, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        JPanel center = AdminTheme.transparent(new BorderLayout(0, 20));

        JPanel cardsGrid = AdminTheme.transparent(new GridLayout(1, 5, 14, 0));
        cardsGrid.add(AdminTheme.card("Trực Tuyến", onlineUsersLabel, "Người chơi online"));
        cardsGrid.add(AdminTheme.card("Bàn Hoạt Động", activeRoomsLabel, "Bàn đang có người / chơi"));
        cardsGrid.add(AdminTheme.card("Tổng Tài Khoản", totalUsersLabel, "Đã đăng ký trong CSDL"));
        cardsGrid.add(AdminTheme.card("Tổng Điểm Lưu Hành", totalBalanceLabel, "Tổng số dư toàn server"));
        cardsGrid.add(AdminTheme.card("Số Ván Hôm Nay", todayRoundsLabel, "Ván đã hoàn tất từ 0h"));
        center.add(cardsGrid, BorderLayout.NORTH);

        JPanel contentArea = AdminTheme.transparent(new BorderLayout());

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

        center.add(contentArea, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        refreshButton.addActionListener(e -> {
            if (onRefreshCallback != null) {
                onRefreshCallback.run();
            }
        });

        broadcastButton.addActionListener(e -> triggerBroadcast());
        broadcastMessageField.addActionListener(e -> triggerBroadcast());
    }

    private void triggerBroadcast() {
        if (!broadcastButton.isEnabled()) return;
        String msg = broadcastMessageField.getText().trim();
        if (msg.isBlank()) {
            broadcastStatusLabel.setText("Vui lòng nhập nội dung thông báo trước khi phát.");
            broadcastStatusLabel.setForeground(AdminTheme.RED);
            return;
        }
        if (msg.length() > PacketIO.MAX_BROADCAST_TEXT) {
            broadcastStatusLabel.setText("Thông báo tối đa " + PacketIO.MAX_BROADCAST_TEXT + " ký tự.");
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

    public void reset() {
        onlineUsersLabel.setText("0");
        activeRoomsLabel.setText("0");
        totalUsersLabel.setText("0");
        totalBalanceLabel.setText("0");
        todayRoundsLabel.setText("0");
        lastUpdatedLabel.setText("Chưa cập nhật");
        broadcastButton.setEnabled(true);
        broadcastMessageField.setText("");
        broadcastLevelCombo.setSelectedIndex(0);
        broadcastStatusLabel.setText(" ");
        broadcastStatusLabel.setForeground(AdminTheme.MUTED);
    }

    public void setOnRefresh(Runnable callback) {
        this.onRefreshCallback = callback;
    }

    public void setOnBroadcast(Consumer<AdminBroadcastRequest> callback) {
        this.onBroadcastCallback = callback;
    }
}
