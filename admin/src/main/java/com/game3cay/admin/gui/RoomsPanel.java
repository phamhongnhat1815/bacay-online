package com.game3cay.admin.gui;

import com.game3cay.shared.model.AdminResetRoomRequest;
import com.game3cay.shared.model.AdminRoomDetail;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Tab Giám Sát Bàn Chơi: Theo dõi 20 bàn chơi thời gian thực và cưỡng chế giải tán bàn kẹt.
 */
public final class RoomsPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[] {"Bàn #", "Tên Bàn", "Trạng Thái", "Cược Sàn", "Quỹ Pot Hiện Tại", "Số Người", "Danh Sách Người Chơi"},
            0
    ) {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable roomsTable = new JTable(tableModel);
    private final List<AdminRoomDetail> currentRooms = new ArrayList<>();

    private final AdminTheme.Button inspectRoomButton = new AdminTheme.Button("SOI BÀN (GOD-VIEW)", "gold");
    private final AdminTheme.Button resetRoomButton = new AdminTheme.Button("GIẢI TÁN / RESET BÀN KẸT", "red");
    private final AdminTheme.Button refreshButton = new AdminTheme.Button("LÀM MỚI", "plain");
    private final JLabel statusLabel = AdminTheme.label("Đang hiển thị 20 bàn chơi", 12, AdminTheme.MUTED);

    private Consumer<Long> onInspectCallback;
    private Consumer<AdminResetRoomRequest> onResetCallback;
    private Runnable onRefreshCallback;

    public RoomsPanel() {
        super(new BorderLayout(0, 16));
        setBackground(AdminTheme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        // Top Toolbar
        JPanel topToolbar = AdminTheme.transparent(new BorderLayout());

        JPanel titlePanel = AdminTheme.transparent(new GridLayout(0, 1, 0, 2));
        titlePanel.add(AdminTheme.label("GIÁM SÁT 20 BÀN CHƠI CỐ ĐỊNH", 20, AdminTheme.GOLD, true));
        titlePanel.add(AdminTheme.label("Cập nhật thời gian thực người chơi, mức cược và quỹ pot của từng bàn", 12, AdminTheme.MUTED));
        topToolbar.add(titlePanel, BorderLayout.WEST);

        JPanel actions = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.add(inspectRoomButton);
        actions.add(resetRoomButton);
        actions.add(refreshButton);
        topToolbar.add(actions, BorderLayout.EAST);
        add(topToolbar, BorderLayout.NORTH);

        // Center Table
        roomsTable.setFont(AdminTheme.font(13, false));
        roomsTable.setRowHeight(36);
        roomsTable.setBackground(AdminTheme.PANEL);
        roomsTable.setForeground(AdminTheme.TEXT);
        roomsTable.setGridColor(new Color(25, 42, 68));
        roomsTable.setSelectionBackground(new Color(24, 48, 82));
        roomsTable.setSelectionForeground(AdminTheme.GOLD);
        roomsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomsTable.setShowVerticalLines(false);

        var header = roomsTable.getTableHeader();
        header.setBackground(new Color(18, 34, 58));
        header.setForeground(AdminTheme.GOLD);
        header.setFont(AdminTheme.font(12, true));
        header.setPreferredSize(new Dimension(200, 38));
        header.setReorderingAllowed(false);

        roomsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? AdminTheme.PANEL : AdminTheme.PANEL_ALT);
                    if (col == 2) {
                        String st = String.valueOf(value);
                        c.setForeground("PLAYING".equals(st) ? AdminTheme.GREEN : "RESULT".equals(st) ? AdminTheme.GOLD : AdminTheme.MUTED);
                    } else if (col == 4) {
                        c.setForeground(AdminTheme.GOLD);
                    } else {
                        c.setForeground(AdminTheme.TEXT);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(roomsTable);
        scrollPane.getViewport().setBackground(AdminTheme.PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(AdminTheme.BORDER));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom
        JPanel bottomBar = AdminTheme.transparent(new BorderLayout());
        bottomBar.add(statusLabel, BorderLayout.WEST);
        add(bottomBar, BorderLayout.SOUTH);

        // Actions
        inspectRoomButton.addActionListener(e -> handleInspectRoom());

        roomsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && roomsTable.getSelectedRow() >= 0) {
                    handleInspectRoom();
                }
            }
        });

        refreshButton.addActionListener(e -> {
            if (onRefreshCallback != null) {
                onRefreshCallback.run();
            }
        });

        resetRoomButton.addActionListener(e -> handleResetRoom());
    }

    private void handleInspectRoom() {
        int selectedRow = roomsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentRooms.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một bàn chơi trên danh sách để soi bài (God-View).", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AdminRoomDetail room = currentRooms.get(selectedRow);
        if (onInspectCallback != null) {
            onInspectCallback.accept(room.roomId());
        }
    }

    private void handleResetRoom() {
        int selectedRow = roomsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentRooms.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một bàn chơi trên danh sách để giải tán.", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AdminRoomDetail room = currentRooms.get(selectedRow);

        String reason = JOptionPane.showInputDialog(
                this,
                "Nhập lý do giải tán / đặt lại bàn #" + room.roomId() + " (" + room.roomName() + "):",
                "Cưỡng Chế Giải Tán Bàn",
                JOptionPane.WARNING_MESSAGE
        );

        if (reason != null && !reason.isBlank() && onResetCallback != null) {
            onResetCallback.accept(new AdminResetRoomRequest(room.roomId(), reason.trim()));
        }
    }

    public void updateRooms(List<AdminRoomDetail> rooms) {
        currentRooms.clear();
        tableModel.setRowCount(0);

        if (rooms != null) {
            currentRooms.addAll(rooms);
            int activeCount = 0;

            for (AdminRoomDetail r : rooms) {
                if (r.playerCount() > 0 || !"WAITING".equals(r.status())) {
                    activeCount++;
                }

                String playersStr = r.playerNames().isEmpty() ? "—" : String.join(", ", r.playerNames());
                tableModel.addRow(new Object[] {
                        "#" + r.roomId(),
                        r.roomName(),
                        r.status(),
                        AdminTheme.points(r.ante()),
                        AdminTheme.points(r.currentPot()),
                        r.playerCount() + " / " + r.maxPlayers(),
                        playersStr
                });
            }

            statusLabel.setText("Có " + activeCount + " / " + rooms.size() + " bàn đang hoạt động · Cập nhật lúc "
                    + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
    }

    public void setOnInspect(Consumer<Long> callback) {
        this.onInspectCallback = callback;
    }

    public void setOnReset(Consumer<AdminResetRoomRequest> callback) {
        this.onResetCallback = callback;
    }

    public void setOnRefresh(Runnable callback) {
        this.onRefreshCallback = callback;
    }
}
