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
import java.awt.GradientPaint;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Theme {
    public static final Color BG = new Color(12, 19, 31);
    public static final Color PANEL = new Color(20, 30, 46);
    public static final Color SURFACE = new Color(27, 40, 58);
    public static final Color LINE = new Color(48, 65, 86);
    public static final Color GOLD = new Color(240, 198, 112);
    public static final Color TEXT = new Color(237, 242, 249);
    public static final Color MUTED = new Color(163, 179, 199);
    public static final Color GREEN = new Color(104, 215, 177);
    public static final Color RED = new Color(255, 153, 149);

    private Theme() {}

    public static Font font(float size, boolean isBold) {
        return new Font("Segoe UI", isBold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    public static String points(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN"))).format(amount);
    }

    public static JLabel label(String text, int fontSize, Color color) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
        label.setFont(font(fontSize, false));
        label.setForeground(color);
        return label;
    }

    public static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    public static void field(JTextField field) {
        field.setFont(font(15, false));
        field.setForeground(TEXT);
        field.setCaretColor(GOLD);
        field.setBackground(BG);
        field.setDisabledTextColor(MUTED);
        field.setSelectionColor(new Color(63, 85, 116));
        field.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(LINE),
                        new EmptyBorder(10, 12, 10, 12)
                )
        );
        field.setPreferredSize(new Dimension(220, 42));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { outline(GOLD); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { outline(LINE); }
            private void outline(Color color) {
                field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color),
                        new EmptyBorder(10, 12, 10, 12)));
            }
        });
    }

    public static void install() {
        UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", font(14, false));
        UIManager.put("Button.font", font(14, true));
        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.disabledText", MUTED);
        UIManager.put("TextField.font", font(15, false));
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", LINE);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.font", font(14, false));
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", MUTED);
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.contentAreaColor", PANEL);
        UIManager.put("TabbedPane.font", font(14, true));
        UIManager.put("PopupMenu.background", PANEL);
        UIManager.put("MenuItem.background", PANEL);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", LINE);
        UIManager.put("MenuItem.selectionForeground", GOLD);
        UIManager.put("MenuItem.font", font(14, false));
        UIManager.put("ToolTip.background", PANEL);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.background", BG);
        UIManager.put("ScrollBar.thumb", LINE);
        UIManager.put("ScrollBar.thumbDarkShadow", LINE);
        UIManager.put("ScrollBar.thumbShadow", LINE);
        UIManager.put("ScrollBar.thumbHighlight", LINE);
    }

    public static void table(javax.swing.JTable table) {
        table.setRowHeight(40);
        table.setFont(font(13, false));
        table.setBackground(PANEL);
        table.setForeground(TEXT);
        table.setGridColor(LINE);
        table.setSelectionBackground(new Color(43, 63, 87));
        table.setSelectionForeground(TEXT);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(12, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(SURFACE);
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setFont(font(12, true));
        table.getTableHeader().setPreferredSize(new Dimension(200, 40));
    }

    public static void tabs(javax.swing.JTabbedPane tabs) {
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                tabInsets = new java.awt.Insets(10, 18, 10, 18);
                selectedTabPadInsets = new java.awt.Insets(0, 0, 0, 0);
                contentBorderInsets = new java.awt.Insets(8, 0, 0, 0);
            }
            @Override protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
                g.setColor(selected ? SURFACE : BG);
                g.fillRoundRect(x, y, w - 3, h, 10, 10);
            }
            @Override protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
                if (selected) { g.setColor(GOLD); g.fillRect(x + 12, y + h - 3, w - 27, 2); }
            }
            @Override protected void paintContentBorder(Graphics g, int placement, int selected) {}
            @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rectangles,
                    int index, Rectangle icon, Rectangle text, boolean selected) {
                if (tabPane.hasFocus() && selected) {
                    Rectangle r = rectangles[index]; g.setColor(GOLD);
                    g.drawRoundRect(r.x + 2, r.y + 2, r.width - 7, r.height - 4, 8, 8);
                }
            }
        });
        tabs.setFont(font(13, true));
        tabs.setForeground(TEXT);
    }

    /** A hint supplements the accessible name; it never replaces a field label. */
    public static final class HintField extends JTextField {
        private final String hint;
        public HintField(String hint) {
            this.hint = hint;
            getAccessibleContext().setAccessibleName(hint);
        }
        @Override protected void paintComponent(Graphics gg) {
            super.paintComponent(gg);
            if (getText().isEmpty() && !hasFocus()) {
                Graphics2D g = (Graphics2D) gg.create();
                Assets.quality(g);
                g.setFont(getFont());
                g.setColor(MUTED);
                g.clipRect(getInsets().left, 0, Math.max(0, getWidth() - getInsets().left - getInsets().right), getHeight());
                g.drawString(hint, getInsets().left, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
                g.dispose();
            }
        }
    }

    public static class Button extends JButton {
        private final String colorVariant;

        public Button(String text, String colorVariant) {
            super(text);
            this.colorVariant = switch (colorVariant == null ? "" : colorVariant) {
                case "plain", "blue", "gold", "green", "orange", "red", "disabled" -> colorVariant;
                default -> "blue";
            };
            setFont(font(14, true));
            setForeground(TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(4, 10, 4, 10));
            setPreferredSize(new Dimension(Math.max(110, text.length() * 9 + 28), 44));
            addActionListener(e -> Sound.play("button_click"));
        }

        @Override public Color getForeground() {
            if (!isEnabled()) return MUTED;
            if ("gold".equals(colorVariant)) return new Color(46, 34, 15);
            if ("plain".equals(colorVariant) && Boolean.TRUE.equals(getClientProperty("active"))) return GOLD;
            return TEXT;
        }

        @Override
        protected void paintComponent(Graphics gg) {
            Graphics2D g = (Graphics2D) gg.create();
            Assets.quality(g);

            boolean active = Boolean.TRUE.equals(getClientProperty("active"));
            Color fill = !isEnabled() ? new Color(30, 40, 55) : switch (colorVariant) {
                case "gold" -> GOLD;
                case "green" -> new Color(35, 110, 87);
                case "red" -> new Color(133, 51, 59);
                case "orange" -> new Color(144, 86, 39);
                case "blue" -> new Color(44, 81, 124);
                default -> active ? new Color(66, 55, 37) : SURFACE;
            };
            if (isEnabled() && getModel().isPressed()) fill = fill.darker();
            else if (isEnabled() && getModel().isRollover()) fill = fill.brighter();
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g.setColor(isEnabled() && (active || "gold".equals(colorVariant)) ? GOLD : LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.dispose();
            super.paintComponent(gg);

            if (hasFocus()) {
                Graphics2D focusG = (Graphics2D) gg.create();
                focusG.setColor("gold".equals(colorVariant) ? BG : GOLD);
                focusG.setStroke(new BasicStroke(2f));
                focusG.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 10, 10);
                focusG.dispose();
            }
        }
    }

    public static class Glass extends JPanel {
        public Glass(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics gg) {
            Graphics2D g = (Graphics2D) gg.create();
            Assets.quality(g);
            g.setPaint(new GradientPaint(0, 0, PANEL, getWidth(), getHeight(), new Color(16, 26, 42)));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g.setColor(LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            g.dispose();
            super.paintComponent(gg);
        }
    }
}
