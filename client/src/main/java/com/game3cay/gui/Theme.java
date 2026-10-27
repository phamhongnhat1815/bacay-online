package com.game3cay.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Hệ thống giao diện chuẩn (Theme & Styling) phong cách Royal Navy & Gold cho Ba Cây Online.
 */
public final class Theme {

    public static final Color BG = new Color(8, 16, 28);
    public static final Color PANEL = new Color(12, 22, 38);
    public static final Color GOLD = new Color(230, 190, 105);
    public static final Color TEXT = new Color(246, 241, 226);
    public static final Color MUTED = new Color(152, 172, 196);
    public static final Color GREEN = new Color(80, 200, 160);
    public static final Color RED = new Color(245, 131, 123);

    private Theme() {}

    /**
     * Tạo Font chữ SansSerif với kích thước và độ đậm quy định.
     */
    public static Font font(float size, boolean isBold) {
        return new Font("SansSerif", isBold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    /**
     * Định dạng số điểm sang chuỗi hiển thị có dấu phân cách hàng nghìn.
     */
    public static String points(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return new DecimalFormat("#,##0.##").format(amount);
    }

    /**
     * Tạo JLabel vô hiệu hóa mã HTML injection, giữ văn bản an toàn.
     */
    public static JLabel label(String text, int fontSize, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(fontSize, false));
        label.setForeground(color);
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    /**
     * Tạo JPanel trong suốt (transparent panel) với bố cục quy định.
     */
    public static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Định dạng giao diện ô nhập liệu JTextField (màu nền navy, viền mảnh, chữ vàng/trắng).
     */
    public static void field(JTextField field) {
        field.setFont(font(15, false));
        field.setForeground(TEXT);
        field.setCaretColor(GOLD);
        field.setBackground(new Color(11, 21, 37));
        field.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(42, 68, 105)),
                        new EmptyBorder(10, 12, 10, 12)
                )
        );
        field.setPreferredSize(new Dimension(220, 42));
    }

    /**
     * Cài đặt các thuộc tính giao diện mặc định cho toàn bộ UIManager của Swing.
     */
    public static void install() {
        UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", font(14, false));
        UIManager.put("Button.font", font(14, true));
        UIManager.put("TextField.font", font(15, false));
        UIManager.put("ToolTip.background", PANEL);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ScrollBar.width", 10);
    }

    /**
     * Nút bấm tùy chỉnh phong cách hoàng gia với các biến thể (gold, red, plain).
     */
    public static class Button extends JButton {
        private final String colorVariant;

        public Button(String text, String colorVariant) {
            super(text);
            this.colorVariant = colorVariant;
            setFont(font(14, true));
            setForeground(TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(10, 12, 10, 12));
            setPreferredSize(new Dimension(Math.max(110, text.length() * 9 + 28), 44));
            addActionListener(e -> Sound.play("button_click"));
        }

        @Override
        protected void paintComponent(Graphics gg) {
            Graphics2D g = (Graphics2D) gg.create();
            Assets.quality(g);

            if (!"plain".equals(colorVariant)) {
                String assetName = isEnabled() ? colorVariant : "disabled";
                g.drawImage(
                        Assets.get("buttons/" + assetName + ".png", 420),
                        0,
                        0,
                        getWidth(),
                        getHeight(),
                        null
                );
            } else {
                Color fill = Boolean.TRUE.equals(getClientProperty("active"))
                        ? new Color(230, 190, 105, 27)
                        : getModel().isRollover()
                        ? new Color(255, 255, 255, 23)
                        : new Color(255, 255, 255, 7);
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

                Color stroke = Boolean.TRUE.equals(getClientProperty("active"))
                        ? GOLD
                        : new Color(65, 88, 120);
                g.setColor(stroke);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }

            if (getModel().isRollover() && isEnabled()) {
                g.setColor(new Color(255, 255, 255, 20));
                g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 12, 12);
            }

            g.dispose();

            if (isEnabled()) {
                setForeground("gold".equals(colorVariant) ? new Color(48, 30, 9) : TEXT);
            } else {
                setForeground(MUTED);
            }

            super.paintComponent(gg);

            if (hasFocus()) {
                Graphics2D focusG = (Graphics2D) gg.create();
                focusG.setColor(GOLD);
                focusG.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 10, 10);
                focusG.dispose();
            }
        }
    }

    /**
     * Bảng nền hiệu ứng thủy tinh trong mờ (Glass Panel) với viền vàng kim bo góc.
     */
    public static class Glass extends JPanel {
        public Glass(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics gg) {
            Graphics2D g = (Graphics2D) gg.create();
            Assets.quality(g);
            g.setColor(new Color(8, 18, 34, 235));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            g.setColor(new Color(212, 175, 55, 160));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            g.dispose();
            super.paintComponent(gg);
        }
    }
}
