package com.game3cay.gui;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;

public final class AuthPanel extends JPanel {
    private final JTextField username = new JTextField();
    private final JTextField name = new JTextField();
    private final JTextField email = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JPasswordField confirm = new JPasswordField();

    private final Theme.Button loginTab = new Theme.Button("Đăng nhập", "plain");
    private final Theme.Button registerTab = new Theme.Button("Đăng ký", "plain");
    private final Theme.Button submitButton = new Theme.Button("VÀO SẢNH CHƠI", "gold");
    private final Theme.Button settingsButton = new Theme.Button("Máy chủ", "plain");

    private final JTextArea messageArea = new JTextArea();
    private final JLabel captionLabel = Theme.label("Chào mừng bạn trở lại", 23, Theme.TEXT);
    private final JPanel extraFieldsPanel = Theme.transparent(new GridLayout(0, 1, 0, 12));
    private final Theme.Glass formContainer = new Theme.Glass(new BorderLayout(0, 20));

    private boolean isRegisterMode;

    public AuthPanel() {
        setLayout(null);
        setBackground(Theme.BG);

        formContainer.setBorder(new EmptyBorder(24, 28, 24, 28));

        var tabsPanel = Theme.transparent(new GridLayout(1, 2, 10, 0));
        tabsPanel.add(loginTab);
        tabsPanel.add(registerTab);

        var topHeader = Theme.transparent(new BorderLayout(0, 16));
        topHeader.add(tabsPanel, BorderLayout.NORTH);
        topHeader.add(captionLabel, BorderLayout.CENTER);
        formContainer.add(topHeader, BorderLayout.NORTH);

        var basicFields = Theme.transparent(new GridLayout(0, 1, 0, 12));
        basicFields.add(createFieldRow("TÀI KHOẢN", username));
        basicFields.add(createFieldRow("MẬT KHẨU", password));

        extraFieldsPanel.add(createFieldRow("NHẬP LẠI MẬT KHẨU", confirm));
        var optionalFields = Theme.transparent(new GridLayout(1, 2, 12, 0));
        optionalFields.add(createFieldRow("TÊN HIỂN THỊ · TÙY CHỌN", name));
        optionalFields.add(createFieldRow("EMAIL · TÙY CHỌN", email));
        extraFieldsPanel.add(optionalFields);
        extraFieldsPanel.setPreferredSize(new Dimension(360, 138));

        var fieldsStack = Theme.transparent(new BorderLayout(0, 12));
        fieldsStack.add(basicFields, BorderLayout.NORTH);
        fieldsStack.add(extraFieldsPanel, BorderLayout.CENTER);

        var middlePanel = Theme.transparent(new BorderLayout());
        middlePanel.add(fieldsStack, BorderLayout.NORTH);
        basicFields.setPreferredSize(new Dimension(360, 138));
        var fieldsScroll = new JScrollPane(middlePanel);
        fieldsScroll.setBorder(null);
        fieldsScroll.setOpaque(false);
        fieldsScroll.getViewport().setOpaque(false);
        fieldsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fieldsScroll.getVerticalScrollBar().setUnitIncrement(20);
        formContainer.add(fieldsScroll, BorderLayout.CENTER);

        messageArea.setEditable(false);
        messageArea.setFocusable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setOpaque(false);
        messageArea.setForeground(Theme.RED);
        messageArea.setFont(Theme.font(12, false));
        messageArea.setPreferredSize(new Dimension(360, 36));

        var bottomActions = Theme.transparent(new BorderLayout(0, 10));
        bottomActions.add(messageArea, BorderLayout.NORTH);
        bottomActions.add(submitButton, BorderLayout.CENTER);
        formContainer.add(bottomActions, BorderLayout.SOUTH);

        add(formContainer);
        add(settingsButton);

        for (var f : new JTextField[]{username, password, confirm, name, email}) {
            Theme.field(f);
        }
        username.getAccessibleContext().setAccessibleName("Tài khoản");
        password.getAccessibleContext().setAccessibleName("Mật khẩu");
        confirm.getAccessibleContext().setAccessibleName("Nhập lại mật khẩu");
        name.getAccessibleContext().setAccessibleName("Tên hiển thị, tùy chọn");
        email.getAccessibleContext().setAccessibleName("Email, tùy chọn");
        password.setToolTipText("Mật khẩu đăng ký từ 8 đến 128 ký tự");

        loginTab.addActionListener(e -> switchMode(false));
        registerTab.addActionListener(e -> switchMode(true));
        switchMode(false);
    }

    private static JPanel createFieldRow(String labelText, JTextField fieldComponent) {
        var rowPanel = Theme.transparent(new BorderLayout(0, 7));
        var label = Theme.label(labelText, 11, Theme.MUTED);
        label.setLabelFor(fieldComponent);
        rowPanel.add(label, BorderLayout.NORTH);
        rowPanel.add(fieldComponent, BorderLayout.CENTER);
        rowPanel.setPreferredSize(new Dimension(170, 66));
        return rowPanel;
    }

    private void switchMode(boolean isRegister) {
        this.isRegisterMode = isRegister;
        extraFieldsPanel.setVisible(isRegister);
        clearPasswords();
        messageArea.setText(" ");

        captionLabel.setText(isRegister ? "Một tài khoản, những cuộc vui" : "Chào mừng bạn trở lại");
        captionLabel.setFont(Theme.font(isRegister ? 20 : 23, true));
        submitButton.setText(isRegister ? "Tạo tài khoản · Nhận 1.000 điểm" : "Đăng nhập vào sảnh →");

        loginTab.putClientProperty("active", !isRegister);
        registerTab.putClientProperty("active", isRegister);
        loginTab.setForeground(isRegister ? Theme.MUTED : Theme.GOLD);
        registerTab.setForeground(isRegister ? Theme.GOLD : Theme.MUTED);

        revalidate();
        repaint();
    }

    @Override
    public void doLayout() {
        int formWidth = Math.min(450, getWidth() - 64);
        int formHeight = Math.min(isRegisterMode ? 620 : 474, Math.max(320, getHeight() - 100));
        int posX = (getWidth() < 1050) ? (getWidth() - formWidth) / 2 : getWidth() - formWidth - 64;
        int posY = Math.max(68, (getHeight() - formHeight) / 2);

        formContainer.setBounds(posX, posY, formWidth, formHeight);
        settingsButton.setBounds(getWidth() - 138, 20, 110, 38);
    }

    @Override
    protected void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g = (Graphics2D) gg.create();
        Assets.quality(g);

        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(24, 44, 65), getWidth(), getHeight(), Theme.BG));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(Theme.GOLD.getRed(), Theme.GOLD.getGreen(), Theme.GOLD.getBlue(), 6));
        g.fillOval(-160, -250, 850, 850);
        g.setColor(new Color(255, 255, 255, 6));
        g.drawOval(-100, -190, 730, 730);

        if (getWidth() >= 1050) {
            int startX = 64;
            int startY = Math.max(115, getHeight() / 2 - 190);

            g.setColor(Theme.GOLD);
            g.setFont(Theme.font(14, true));
            g.drawString("CÙNG BẠN BÈ · CÙNG MỘT BÀN", startX, startY);

            g.setFont(Theme.font(68, true));
            g.drawString("BA CÂY", startX, startY + 89);

            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(21, false));
            g.drawString("Ba lá bài. Một cuộc vui.", startX, startY + 132);

            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(14, false));
            g.drawString("01  Chọn bàn     02  Vào ván     03  Nặn bài", startX, startY + 165);

            Card[] decorativeCards = {
                    new Card(Rank.ACE, Suit.BICH),
                    new Card(Rank.ACE, Suit.CO),
                    new Card(Rank.ACE, Suit.RO)
            };

            for (int i = 0; i < decorativeCards.length; i++) {
                Graphics2D cardG = (Graphics2D) g.create();
                cardG.translate(startX + 80 + i * 110, startY + 212);
                cardG.rotate(Math.toRadians((i - 1) * 12), 66, 160);
                cardG.setColor(new Color(0, 0, 0, 90));
                cardG.fillRoundRect(5, 8, 132, 185, 12, 12);
                Assets.drawCard(cardG, decorativeCards[i], 0, 0, 132, 185);
                cardG.dispose();
            }
        }

        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(12, false));
        g.drawString("BA CÂY ONLINE   /   Điểm giải trí", 32, getHeight() - 26);
        g.dispose();
    }

    public String username() {
        return username.getText().trim();
    }

    public char[] password() {
        return password.getPassword();
    }

    public char[] confirm() {
        return confirm.getPassword();
    }

    public String displayName() {
        return name.getText().trim();
    }

    public String email() {
        return email.getText().trim();
    }

    public boolean register() {
        return isRegisterMode;
    }

    public void onSubmit(Runnable runnable) {
        submitButton.addActionListener(e -> runnable.run());
        password.addActionListener(e -> {
            if (submitButton.isEnabled()) {
                runnable.run();
            }
        });
        confirm.addActionListener(e -> {
            if (submitButton.isEnabled()) {
                runnable.run();
            }
        });
    }

    public void onSettings(Runnable runnable) {
        settingsButton.addActionListener(e -> runnable.run());
    }

    public void clearPasswords() {
        password.setText("");
        confirm.setText("");
    }

    public void completed() {
        switchMode(false);
        password.requestFocusInWindow();
    }

    public void message(String text) {
        messageArea.setToolTipText(text);
        messageArea.setText(text == null || text.isBlank() ? " " : text);
    }

    public void setBusy(boolean busy) {
        for (JComponent comp : new JComponent[]{
                username, password, confirm, name, email, loginTab, registerTab, submitButton, settingsButton
        }) {
            comp.setEnabled(!busy);
        }
        submitButton.setText(busy ? "Đang kết nối…" : isRegisterMode ? "Tạo tài khoản · Nhận 1.000 điểm" : "Đăng nhập vào sảnh →");
    }
}
