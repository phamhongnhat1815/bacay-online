package com.game3cay.admin.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.Serial;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Bảng mã màu và tiện ích dựng giao diện đồng nhất chuẩn Royal Navy & Gold cho Admin.
 */
public final class AdminTheme {

    public static final Color BG = new Color(6, 14, 25);
    public static final Color PANEL = new Color(13, 27, 46);
    public static final Color PANEL_ALT = new Color(18, 36, 62);
    public static final Color GOLD = new Color(212, 175, 55);
    public static final Color GOLD_HOVER = new Color(235, 195, 80);
    public static final Color TEXT = new Color(230, 236, 245);
    public static final Color MUTED = new Color(145, 165, 190);
    public static final Color BORDER = new Color(45, 68, 100);
    public static final Color GREEN = new Color(60, 180, 100);
    public static final Color RED = new Color(230, 75, 75);

    private static final String FONT_NAME = "Segoe UI";

    private AdminTheme() {}

    public static Font font(int size, boolean bold) {
        return new Font(FONT_NAME, bold ? Font.BOLD : Font.PLAIN, size);
    }

    public static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(font(size, bold));
        label.setForeground(color);
        return label;
    }

    public static JLabel label(String text, int size, Color color) {
        return label(text, size, color, false);
    }

    public static String points(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.##", symbols);
        return df.format(value);
    }

    public static void field(JTextField field) {
        field.setFont(font(14, false));
        field.setBackground(new Color(10, 22, 38));
        field.setForeground(TEXT);
        field.setCaretColor(GOLD);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                new EmptyBorder(6, 12, 6, 12)));
    }

    public static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    public static <T extends JComponent> T transparentComponent(T comp) {
        comp.setOpaque(false);
        return comp;
    }

    public static final class Button extends JButton {
        @Serial
        private static final long serialVersionUID = 1L;

        private String variant;

        public Button(String text, String variant) {
            super(text);
            this.variant = variant;
            setFont(font(13, true));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(7, 16, 7, 16));
        }

        public void setVariant(String variant) {
            this.variant = variant;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            var fm = getFontMetrics(getFont());
            int w = (getText() != null ? fm.stringWidth(getText()) : 0) + 32;
            int h = Math.max(38, fm.getHeight() + 16);
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean pressed = getModel().isPressed();
            boolean hover = getModel().isRollover();

            Color bg;
            Color textCol;
            Color borderCol = BORDER;

            if ("gold".equalsIgnoreCase(variant)) {
                bg = pressed ? new Color(180, 145, 35) : hover ? GOLD_HOVER : GOLD;
                textCol = new Color(8, 18, 32);
                borderCol = bg;
            } else if ("red".equalsIgnoreCase(variant)) {
                bg = pressed ? new Color(180, 40, 40) : hover ? new Color(245, 90, 90) : RED;
                textCol = Color.WHITE;
                borderCol = bg;
            } else if ("green".equalsIgnoreCase(variant)) {
                bg = pressed ? new Color(40, 140, 70) : hover ? new Color(75, 205, 115) : GREEN;
                textCol = Color.WHITE;
                borderCol = bg;
            } else {
                bg = pressed ? new Color(20, 38, 64) : hover ? new Color(26, 48, 80) : PANEL;
                textCol = TEXT;
                borderCol = hover ? GOLD : BORDER;
            }

            if (!isEnabled()) {
                bg = new Color(20, 30, 45);
                textCol = new Color(100, 115, 135);
                borderCol = new Color(35, 50, 70);
            }

            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(borderCol);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            g2.setColor(textCol);
            g2.setFont(getFont());
            var fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(getText())) / 2;
            int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
    }

    public static JPanel card(String title, JComponent valueComp, String subtitle) {
        JPanel p = new JPanel() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setLayout(new java.awt.BorderLayout(0, 4));
        p.setBorder(new EmptyBorder(16, 18, 16, 18));
        p.setPreferredSize(new Dimension(210, 105));

        JLabel t = label(title.toUpperCase(), 11, MUTED, true);
        JLabel s = label(subtitle, 11, MUTED);

        p.add(t, java.awt.BorderLayout.NORTH);
        p.add(valueComp, java.awt.BorderLayout.CENTER);
        p.add(s, java.awt.BorderLayout.SOUTH);
        return p;
    }
}
