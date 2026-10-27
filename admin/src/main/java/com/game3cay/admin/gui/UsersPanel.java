package com.game3cay.admin.gui;

import com.game3cay.shared.model.AdminAdjustBalanceRequest;
import com.game3cay.shared.model.AdminLockUserRequest;
import com.game3cay.shared.model.AdminUserFilterRequest;
import com.game3cay.shared.model.AdminUserItem;
import com.game3cay.shared.model.PageResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Tab Quản Lý Người Chơi: Tìm kiếm, khóa/mở khóa tài khoản, điều chỉnh số dư điểm.
 */
public final class UsersPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> statusFilterCombo = new JComboBox<>(new String[] {"Tất cả", "ACTIVE", "BLOCKED"});
    private final AdminTheme.Button searchButton = new AdminTheme.Button("TÌM KIẾM", "plain");

    private final AdminTheme.Button toggleLockButton = new AdminTheme.Button("KHÓA / MỞ KHÓA", "red");
    private final AdminTheme.Button adjustBalanceButton = new AdminTheme.Button("CỘNG / TRỪ ĐIỂM", "gold");

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[] {"ID", "Tên Đăng Nhập", "Tên Hiển Thị", "Email", "Số Dư", "Trạng Thái", "Quyền", "Thắng / Đã Chơi", "Ngày Tạo"},
            0
    ) {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable userTable = new JTable(tableModel);
    private final List<AdminUserItem> currentItems = new ArrayList<>();

    private final AdminTheme.Button prevButton = new AdminTheme.Button("← Trước", "plain");
    private final AdminTheme.Button nextButton = new AdminTheme.Button("Sau →", "plain");
    private final AdminTheme.Button refreshButton = new AdminTheme.Button("Làm Mới", "plain");
    private final JLabel pageStatusLabel = AdminTheme.label("Trang 1 / 1 (0 người)", 12, AdminTheme.MUTED);

    private int currentPage = 0;
    private int totalPages = 0;

    private Consumer<AdminUserFilterRequest> onSearchCallback;
    private Consumer<AdminLockUserRequest> onLockCallback;
    private Consumer<AdminAdjustBalanceRequest> onAdjustCallback;

    public UsersPanel() {
        super(new BorderLayout(0, 16));
        setBackground(AdminTheme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        // Top Toolbar
        JPanel topToolbar = AdminTheme.transparent(new BorderLayout());

        JPanel searchBar = AdminTheme.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchField.setPreferredSize(new Dimension(240, 38));
        AdminTheme.field(searchField);
        searchField.setToolTipText("Tìm theo username, tên hiển thị hoặc email");

        statusFilterCombo.setFont(AdminTheme.font(13, false));
        statusFilterCombo.setBackground(AdminTheme.PANEL);
        statusFilterCombo.setForeground(AdminTheme.TEXT);
        statusFilterCombo.setPreferredSize(new Dimension(110, 38));

        searchBar.add(AdminTheme.label("Tìm kiếm:", 13, AdminTheme.MUTED));
        searchBar.add(searchField);
        searchBar.add(statusFilterCombo);
        searchBar.add(searchButton);
        topToolbar.add(searchBar, BorderLayout.WEST);

        JPanel actionButtons = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionButtons.add(adjustBalanceButton);
        actionButtons.add(toggleLockButton);
        topToolbar.add(actionButtons, BorderLayout.EAST);
        add(topToolbar, BorderLayout.NORTH);

        // Center Table
        userTable.setFont(AdminTheme.font(13, false));
        userTable.setRowHeight(36);
        userTable.setBackground(AdminTheme.PANEL);
        userTable.setForeground(AdminTheme.TEXT);
        userTable.setGridColor(new Color(25, 42, 68));
        userTable.setSelectionBackground(new Color(24, 48, 82));
        userTable.setSelectionForeground(AdminTheme.GOLD);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.setShowVerticalLines(false);

        var header = userTable.getTableHeader();
        header.setBackground(new Color(18, 34, 58));
        header.setForeground(AdminTheme.GOLD);
        header.setFont(AdminTheme.font(12, true));
        header.setPreferredSize(new Dimension(200, 38));
        header.setReorderingAllowed(false);

        userTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? AdminTheme.PANEL : AdminTheme.PANEL_ALT);
                    if (col == 5) {
                        String st = String.valueOf(value);
                        c.setForeground("BLOCKED".equals(st) ? AdminTheme.RED : AdminTheme.GREEN);
                    } else if (col == 4) {
                        c.setForeground(AdminTheme.GOLD);
                    } else if (col == 6) {
                        String role = String.valueOf(value);
                        c.setForeground("ADMIN".equals(role) ? AdminTheme.GOLD : AdminTheme.TEXT);
                    } else {
                        c.setForeground(AdminTheme.TEXT);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(userTable);
        scrollPane.getViewport().setBackground(AdminTheme.PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(AdminTheme.BORDER));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Pagination
        JPanel bottomBar = AdminTheme.transparent(new BorderLayout());
        bottomBar.add(pageStatusLabel, BorderLayout.WEST);

        JPanel pageNav = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        pageNav.add(prevButton);
        pageNav.add(nextButton);
        pageNav.add(refreshButton);
        bottomBar.add(pageNav, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        // Events
        searchButton.addActionListener(e -> executeSearch(0));
        searchField.addActionListener(e -> executeSearch(0));
        statusFilterCombo.addActionListener(e -> executeSearch(0));
        refreshButton.addActionListener(e -> executeSearch(currentPage));

        prevButton.addActionListener(e -> {
            if (currentPage > 0) {
                executeSearch(currentPage - 1);
            }
        });
        nextButton.addActionListener(e -> {
            if (currentPage + 1 < totalPages) {
                executeSearch(currentPage + 1);
            }
        });

        toggleLockButton.addActionListener(e -> handleToggleLock());
        adjustBalanceButton.addActionListener(e -> handleAdjustBalance());
    }

    private void executeSearch(int page) {
        if (onSearchCallback == null) {
            return;
        }
        String q = searchField.getText().trim();
        String sel = (String) statusFilterCombo.getSelectedItem();
        String statusFilter = "Tất cả".equals(sel) ? "ALL" : sel;

        onSearchCallback.accept(new AdminUserFilterRequest(q, statusFilter, page, 20));
    }

    private void handleToggleLock() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentItems.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng trên bảng để thao tác.", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AdminUserItem user = currentItems.get(selectedRow);
        if ("ADMIN".equalsIgnoreCase(user.role())) {
            JOptionPane.showMessageDialog(this, "Không thể khóa tài khoản Quản trị viên.", "Từ chối", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean willBlock = "ACTIVE".equalsIgnoreCase(user.status());
        String actionName = willBlock ? "KHÓA" : "MỞ KHÓA";

        int opt = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc muốn " + actionName + " tài khoản [" + user.username() + "] (" + user.displayName() + ")?",
                "Xác nhận " + actionName,
                JOptionPane.YES_NO_OPTION
        );

        if (opt == JOptionPane.YES_OPTION && onLockCallback != null) {
            onLockCallback.accept(new AdminLockUserRequest(user.userId(), willBlock));
        }
    }

    private void handleAdjustBalance() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentItems.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng để điều chỉnh số dư.", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AdminUserItem user = currentItems.get(selectedRow);

        JTextField amountField = new JTextField();
        JTextField reasonField = new JTextField("Admin điều chỉnh điểm");
        AdminTheme.field(amountField);
        AdminTheme.field(reasonField);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
        form.add(AdminTheme.label("Người dùng: " + user.displayName() + " (@" + user.username() + ")", 13, AdminTheme.GOLD, true));
        form.add(AdminTheme.label("Số dư hiện tại: " + AdminTheme.points(user.balance()) + " điểm", 13, AdminTheme.MUTED));
        form.add(AdminTheme.label("Số điểm cần thay đổi (Dương để cộng, Âm để trừ):", 12, AdminTheme.TEXT));
        form.add(amountField);
        form.add(AdminTheme.label("Lý do điều chỉnh (lưu sổ cái kiểm toán):", 12, AdminTheme.TEXT));
        form.add(reasonField);

        int opt = JOptionPane.showConfirmDialog(this, form, "Điều Chỉnh Số Dư Điểm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opt == JOptionPane.OK_OPTION) {
            try {
                BigDecimal delta = new BigDecimal(amountField.getText().trim());
                if (delta.signum() == 0 || delta.scale() > 2) {
                    throw new NumberFormatException();
                }
                if (user.balance().add(delta).signum() < 0) {
                    JOptionPane.showMessageDialog(this, "Số dư sau điều chỉnh không thể âm.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (onAdjustCallback != null) {
                    onAdjustCallback.accept(new AdminAdjustBalanceRequest(user.userId(), delta, reasonField.getText().trim()));
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập số hợp lệ, tối đa 2 chữ số thập phân.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void updatePage(PageResult<AdminUserItem> result) {
        currentItems.clear();
        tableModel.setRowCount(0);

        if (result != null) {
            this.currentPage = result.page();
            this.totalPages = result.totalPages();
            currentItems.addAll(result.items());

            for (AdminUserItem u : result.items()) {
                tableModel.addRow(new Object[] {
                        u.userId(),
                        u.username(),
                        u.displayName(),
                        u.email() != null ? u.email() : "—",
                        AdminTheme.points(u.balance()),
                        u.status(),
                        u.role(),
                        u.totalWins() + " / " + u.totalGames(),
                        TIME_FMT.format(Instant.ofEpochMilli(u.createdAt()))
                });
            }

            pageStatusLabel.setText(result.totalItems() == 0
                    ? "Không tìm thấy người dùng phù hợp"
                    : "Trang " + (currentPage + 1) + " / " + totalPages + " (" + result.totalItems() + " người)");
        }

        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage + 1 < totalPages);
    }

    public void setOnSearch(Consumer<AdminUserFilterRequest> callback) {
        this.onSearchCallback = callback;
    }

    public void setOnLock(Consumer<AdminLockUserRequest> callback) {
        this.onLockCallback = callback;
    }

    public void setOnAdjust(Consumer<AdminAdjustBalanceRequest> callback) {
        this.onAdjustCallback = callback;
    }

    public int getCurrentPage() {
        return currentPage;
    }
}
