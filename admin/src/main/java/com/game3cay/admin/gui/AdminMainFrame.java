package com.game3cay.admin.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.io.Serial;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public final class AdminMainFrame extends JFrame {
    @Serial
    private static final long serialVersionUID = 1L;

    private final CardLayout rootLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(rootLayout);

    private final AdminAuthPanel authPanel = new AdminAuthPanel();

    private final CardLayout tabsLayout = new CardLayout();
    private final JPanel tabsContainer = AdminTheme.transparent(tabsLayout);

    private final DashboardPanel dashboardPanel = new DashboardPanel();
    private final UsersPanel usersPanel = new UsersPanel();
    private final RoomsPanel roomsPanel = new RoomsPanel();
    private final LedgerPanel ledgerPanel = new LedgerPanel();

    private final JLabel adminInfoLabel = AdminTheme.label("", 14, AdminTheme.GOLD, true);
    private final JLabel statusMessageLabel = AdminTheme.label("Sẵn sàng", 12, AdminTheme.MUTED);

    private final AdminTheme.Button navDashboard = new AdminTheme.Button("TỔNG QUAN", "gold");
    private final AdminTheme.Button navUsers = new AdminTheme.Button("NGƯỜI CHƠI", "plain");
    private final AdminTheme.Button navRooms = new AdminTheme.Button("GIÁM SÁT BÀN", "plain");
    private final AdminTheme.Button navLedger = new AdminTheme.Button("SỔ CÁI", "plain");
    private final AdminTheme.Button logoutButton = new AdminTheme.Button("Đăng Xuất", "plain");

    private Runnable onLogoutCallback;

    public AdminMainFrame() {
        super("Ba Cây Online — Bảng Quản Trị Hệ Thống");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(new Color(8, 18, 32));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AdminTheme.BORDER),
                new EmptyBorder(12, 24, 12, 24)
        ));

        JPanel brand = AdminTheme.transparent(new GridLayout(0, 1, 0, 2));
        brand.add(AdminTheme.label("♠ BA CÂY ADMIN", 20, AdminTheme.GOLD, true));
        brand.add(AdminTheme.label("CONSOLE / SYSTEM MANAGEMENT", 9, AdminTheme.MUTED));
        header.add(brand, BorderLayout.WEST);

        JPanel navBar = AdminTheme.transparent(new FlowLayout(FlowLayout.CENTER, 8, 0));
        navBar.add(navDashboard);
        navBar.add(navUsers);
        navBar.add(navRooms);
        navBar.add(navLedger);
        header.add(navBar, BorderLayout.CENTER);

        JPanel rightBar = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightBar.add(adminInfoLabel);
        rightBar.add(Box.createHorizontalStrut(6));
        rightBar.add(logoutButton);
        header.add(rightBar, BorderLayout.EAST);

        tabsContainer.add(dashboardPanel, "DASHBOARD");
        tabsContainer.add(usersPanel, "USERS");
        tabsContainer.add(roomsPanel, "ROOMS");
        tabsContainer.add(ledgerPanel, "LEDGER");

        JPanel consoleScreen = new JPanel(new BorderLayout());
        consoleScreen.setBackground(AdminTheme.BG);
        consoleScreen.add(header, BorderLayout.NORTH);
        consoleScreen.add(tabsContainer, BorderLayout.CENTER);

        JPanel footer = AdminTheme.transparent(new BorderLayout());
        footer.setBorder(new EmptyBorder(8, 24, 8, 24));
        footer.add(statusMessageLabel, BorderLayout.WEST);
        footer.add(AdminTheme.label("BA CÂY ADMIN v2.0", 11, AdminTheme.MUTED), BorderLayout.EAST);
        consoleScreen.add(footer, BorderLayout.SOUTH);

        rootPanel.add(authPanel, "AUTH");
        rootPanel.add(consoleScreen, "CONSOLE");
        setContentPane(rootPanel);

        var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(1100, 680));
        setSize(Math.min(1360, screen.width - 40), Math.min(840, screen.height - 40));
        setLocationRelativeTo(null);

        navDashboard.addActionListener(e -> selectTab("DASHBOARD", navDashboard));
        navUsers.addActionListener(e -> selectTab("USERS", navUsers));
        navRooms.addActionListener(e -> selectTab("ROOMS", navRooms));
        navLedger.addActionListener(e -> selectTab("LEDGER", navLedger));

        logoutButton.addActionListener(e -> {
            if (onLogoutCallback != null) {
                onLogoutCallback.run();
            }
        });

        showAuthScreen();
    }

    private void selectTab(String tabName, AdminTheme.Button selectedBtn) {
        tabsLayout.show(tabsContainer, tabName);

        for (var b : new AdminTheme.Button[] {navDashboard, navUsers, navRooms, navLedger}) {
            b.setVariant(b == selectedBtn ? "gold" : "plain");
        }
    }

    public void showAuthScreen() {
        rootLayout.show(rootPanel, "AUTH");
        adminInfoLabel.setText("");
        authPanel.clearPassword();
        authPanel.setBusy(false);
    }

    public void showConsoleScreen(String adminName, String role) {
        adminInfoLabel.setText(adminName + " (" + role + ")");
        rootLayout.show(rootPanel, "CONSOLE");
        selectTab("DASHBOARD", navDashboard);
    }

    public void setStatusMessage(String message, boolean error) {
        statusMessageLabel.setText(message);
        statusMessageLabel.setForeground(error ? AdminTheme.RED : AdminTheme.MUTED);
    }

    public void setOnLogout(Runnable callback) {
        this.onLogoutCallback = callback;
    }

    public AdminAuthPanel getAuthPanel() {
        return authPanel;
    }

    public DashboardPanel getDashboardPanel() {
        return dashboardPanel;
    }

    public UsersPanel getUsersPanel() {
        return usersPanel;
    }

    public RoomsPanel getRoomsPanel() {
        return roomsPanel;
    }

    public LedgerPanel getLedgerPanel() {
        return ledgerPanel;
    }
}
