package com.game3cay.admin.gui;

import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.PageResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.Serial;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public final class LedgerPanel extends JPanel {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[] {"Mã Ván / Giao Dịch", "ID User", "Tên Đăng Nhập", "Biến Động Điểm", "Số Dư Trước GD", "Số Dư Sau GD", "Thời Điểm Ghi Nhận"},
            0
    ) {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable ledgerTable = new JTable(tableModel);

    private final AdminTheme.Button prevButton = new AdminTheme.Button("← Trước", "plain");
    private final AdminTheme.Button nextButton = new AdminTheme.Button("Sau →", "plain");
    private final AdminTheme.Button refreshButton = new AdminTheme.Button("Làm Mới", "plain");
    private final JLabel pageStatusLabel = AdminTheme.label("Trang 1 / 1 (0 giao dịch)", 12, AdminTheme.MUTED);

    private int currentPage = 0;
    private int totalPages = 0;

    private IntConsumer onPageChangeCallback;

    public LedgerPanel() {
        super(new BorderLayout(0, 16));
        setBackground(AdminTheme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        JPanel topBar = AdminTheme.transparent(new BorderLayout());
        JPanel titlePanel = AdminTheme.transparent(new GridLayout(0, 1, 0, 2));
        titlePanel.add(AdminTheme.label("SỔ CÁI KIỂM TOÁN TÀI CHÍNH", 20, AdminTheme.GOLD, true));
        titlePanel.add(AdminTheme.label("Lưu vết toàn bộ biến động điểm số từ kết quả ván bài và can thiệp của Quản trị viên", 12, AdminTheme.MUTED));
        topBar.add(titlePanel, BorderLayout.WEST);

        JPanel actions = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.add(refreshButton);
        topBar.add(actions, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        ledgerTable.setFont(AdminTheme.font(13, false));
        ledgerTable.setRowHeight(36);
        ledgerTable.setBackground(AdminTheme.PANEL);
        ledgerTable.setForeground(AdminTheme.TEXT);
        ledgerTable.setGridColor(new Color(25, 42, 68));
        ledgerTable.setSelectionBackground(new Color(24, 48, 82));
        ledgerTable.setSelectionForeground(AdminTheme.GOLD);
        ledgerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ledgerTable.setShowVerticalLines(false);

        var header = ledgerTable.getTableHeader();
        header.setBackground(new Color(18, 34, 58));
        header.setForeground(AdminTheme.GOLD);
        header.setFont(AdminTheme.font(12, true));
        header.setPreferredSize(new Dimension(200, 38));
        header.setReorderingAllowed(false);

        ledgerTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? AdminTheme.PANEL : AdminTheme.PANEL_ALT);
                    if (col == 3) {
                        String deltaStr = String.valueOf(value);
                        c.setForeground(deltaStr.startsWith("+") ? AdminTheme.GREEN : deltaStr.startsWith("-") ? AdminTheme.RED : AdminTheme.MUTED);
                    } else if (col == 4 || col == 5) {
                        c.setForeground(AdminTheme.GOLD);
                    } else {
                        c.setForeground(AdminTheme.TEXT);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(ledgerTable);
        scrollPane.getViewport().setBackground(AdminTheme.PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(AdminTheme.BORDER));
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomBar = AdminTheme.transparent(new BorderLayout());
        bottomBar.add(pageStatusLabel, BorderLayout.WEST);

        JPanel pageNav = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        pageNav.add(prevButton);
        pageNav.add(nextButton);
        pageNav.add(refreshButton);
        bottomBar.add(pageNav, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> {
            if (onPageChangeCallback != null) {
                onPageChangeCallback.accept(currentPage);
            }
        });

        prevButton.addActionListener(e -> {
            if (currentPage > 0 && onPageChangeCallback != null) {
                onPageChangeCallback.accept(currentPage - 1);
            }
        });

        nextButton.addActionListener(e -> {
            if (currentPage + 1 < totalPages && onPageChangeCallback != null) {
                onPageChangeCallback.accept(currentPage + 1);
            }
        });
    }

    public void updatePage(PageResult<AdminLedgerItem> result) {
        tableModel.setRowCount(0);

        if (result != null) {
            this.currentPage = result.page();
            this.totalPages = result.totalPages();

            for (AdminLedgerItem item : result.items()) {
                String deltaStr = (item.delta().signum() > 0 ? "+" : "") + AdminTheme.points(item.delta());
                tableModel.addRow(new Object[] {
                        item.roundId(),
                        item.userId(),
                        item.username(),
                        deltaStr,
                        AdminTheme.points(item.balanceBefore()),
                        AdminTheme.points(item.balanceAfter()),
                        TIME_FMT.format(Instant.ofEpochMilli(item.timestamp()))
                });
            }

            pageStatusLabel.setText(result.totalItems() == 0
                    ? "Chưa có bản ghi sổ cái nào"
                    : "Trang " + (currentPage + 1) + " / " + totalPages + " (" + result.totalItems() + " giao dịch)");
        }

        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage + 1 < totalPages);
    }

    public void setOnPageChange(IntConsumer callback) {
        this.onPageChangeCallback = callback;
    }

    public int getCurrentPage() {
        return currentPage;
    }
}
