package com.game3cay.admin.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.Serial;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Màn hình đăng nhập tài khoản Quản trị viên (Admin).
 */
public final class AdminAuthPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    public record LoginCredentials(String host, int port, String username, char[] password) {}

    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("8888");
    private final JTextField usernameField = new JTextField("admin");
    private final JPasswordField passwordField = new JPasswordField();

    private final JLabel messageLabel = AdminTheme.label(" ", 13, AdminTheme.MUTED);
    private final AdminTheme.Button loginButton = new AdminTheme.Button("ĐĂNG NHẬP ADMIN", "gold");

    private Consumer<LoginCredentials> onLoginCallback;

    public AdminAuthPanel() {
        super(new BorderLayout());
        setBackground(AdminTheme.BG);

        // Center Card Container
        JPanel centerCard = new JPanel(new BorderLayout(0, 20)) {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AdminTheme.PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(AdminTheme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        centerCard.setOpaque(false);
        centerCard.setBorder(new EmptyBorder(36, 40, 36, 40));
        centerCard.setPreferredSize(new Dimension(460, 560));

        // Header Title
        JPanel titlePanel = AdminTheme.transparent(new GridLayout(0, 1, 0, 4));
        JLabel title = AdminTheme.label("♠ BA CÂY ADMIN", 26, AdminTheme.GOLD, true);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel subtitle = AdminTheme.label("BẢNG ĐIỀU KHIỂN QUẢN TRỊ VIÊN", 11, AdminTheme.MUTED);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        titlePanel.add(title);
        titlePanel.add(subtitle);
        centerCard.add(titlePanel, BorderLayout.NORTH);

        // Fields Panel with BoxLayout
        JPanel fields = new JPanel();
        fields.setOpaque(false);
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));

        AdminTheme.field(hostField);
        AdminTheme.field(portField);
        AdminTheme.field(usernameField);
        AdminTheme.field(passwordField);

        Dimension fieldDim = new Dimension(Integer.MAX_VALUE, 40);
        hostField.setMaximumSize(fieldDim);
        portField.setMaximumSize(fieldDim);
        usernameField.setMaximumSize(fieldDim);
        passwordField.setMaximumSize(fieldDim);

        addField(fields, "Máy chủ (Host / IP)", hostField);
        fields.add(Box.createVerticalStrut(10));
        addField(fields, "Cổng kết nối (Port)", portField);
        fields.add(Box.createVerticalStrut(10));
        addField(fields, "Tài khoản quản trị", usernameField);
        fields.add(Box.createVerticalStrut(10));
        addField(fields, "Mật khẩu", passwordField);

        centerCard.add(fields, BorderLayout.CENTER);

        // Actions & Status
        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));

        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        south.add(messageLabel);
        south.add(Box.createVerticalStrut(10));

        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        loginButton.setPreferredSize(new Dimension(380, 44));
        loginButton.addActionListener(e -> triggerLogin());
        passwordField.addActionListener(e -> triggerLogin());
        south.add(loginButton);

        centerCard.add(south, BorderLayout.SOUTH);

        JPanel outer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 50));
        outer.setOpaque(false);
        outer.add(centerCard);
        add(outer, BorderLayout.CENTER);
    }

    private void addField(JPanel parent, String labelText, JTextField field) {
        JLabel lbl = AdminTheme.label(labelText, 12, AdminTheme.MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl);
        parent.add(Box.createVerticalStrut(4));
        parent.add(field);
    }

    private void triggerLogin() {
        if (onLoginCallback == null) {
            return;
        }

        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();

        if (host.isBlank() || portText.isBlank() || username.isBlank() || password.length == 0) {
            showError("Vui lòng nhập đầy đủ thông tin kết nối và tài khoản");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showError("Cổng kết nối phải là số nguyên từ 1 đến 65535");
            return;
        }

        setBusy(true);
        messageLabel.setText("Đang kết nối tới máy chủ…");
        messageLabel.setForeground(AdminTheme.MUTED);

        onLoginCallback.accept(new LoginCredentials(host, port, username, password));
    }

    public void setOnLogin(Consumer<LoginCredentials> callback) {
        this.onLoginCallback = callback;
    }

    public void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        hostField.setEnabled(!busy);
        portField.setEnabled(!busy);
        usernameField.setEnabled(!busy);
        passwordField.setEnabled(!busy);
    }

    public void showError(String message) {
        setBusy(false);
        messageLabel.setText(message);
        messageLabel.setForeground(AdminTheme.RED);
    }

    public void clearPassword() {
        passwordField.setText("");
    }
}
