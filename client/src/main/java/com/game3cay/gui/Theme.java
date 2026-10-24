package com.game3cay.gui;

import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class Theme {
  public static final Color BG = new Color(8, 16, 28),
      PANEL = new Color(12, 22, 38),
      GOLD = new Color(230, 190, 105),
      TEXT = new Color(246, 241, 226),
      MUTED = new Color(152, 172, 196),
      GREEN = new Color(80, 200, 160),
      RED = new Color(245, 131, 123);

  private Theme() {}

  public static Font font(float size, boolean bold) {
    return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, Math.round(size));
  }

  public static String points(BigDecimal n) {
    return n == null ? "0" : new DecimalFormat("#,##0.##").format(n);
  }

  public static JLabel label(String text, int size, Color color) {
    var l = new JLabel(text);
    l.setFont(font(size, false));
    l.setForeground(color);
    l.putClientProperty("html.disable", true);
    return l;
  }

  public static JPanel transparent(LayoutManager layout) {
    var p = new JPanel(layout);
    p.setOpaque(false);
    return p;
  }

  public static void field(JTextField field) {
    field.setFont(font(15, false));
    field.setForeground(TEXT);
    field.setCaretColor(GOLD);
    field.setBackground(new Color(11, 21, 37));
    field.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(42, 68, 105)),
            new EmptyBorder(10, 12, 10, 12)));
    field.setPreferredSize(new Dimension(220, 42));
  }

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

  public static class Button extends JButton {
    private final String color;

    public Button(String text, String color) {
      super(text);
      this.color = color;
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
      var g = (Graphics2D) gg.create();
      Assets.quality(g);
      if (!"plain".equals(color)) {
        g.drawImage(
            Assets.get("buttons/" + (isEnabled() ? color : "disabled") + ".png", 420),
            0,
            0,
            getWidth(),
            getHeight(),
            null);
      } else {
        g.setColor(
            Boolean.TRUE.equals(getClientProperty("active"))
                ? new Color(230, 190, 105, 27)
                : getModel().isRollover()
                    ? new Color(255, 255, 255, 23)
                    : new Color(255, 255, 255, 7));
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g.setColor(
            Boolean.TRUE.equals(getClientProperty("active")) ? GOLD : new Color(65, 88, 120));
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
      }
      if (getModel().isRollover() && isEnabled()) {
        g.setColor(new Color(255, 255, 255, 20));
        g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 12, 12);
      }
      g.dispose();
      setForeground(isEnabled() ? ("gold".equals(color) ? new Color(48, 30, 9) : TEXT) : MUTED);
      super.paintComponent(gg);
      if (hasFocus()) {
        var f = (Graphics2D) gg.create();
        f.setColor(GOLD);
        f.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 10, 10);
        f.dispose();
      }
    }
  }

  public static class Glass extends JPanel {
    public Glass(LayoutManager l) {
      super(l);
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics gg) {
      var g = (Graphics2D) gg.create();
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
