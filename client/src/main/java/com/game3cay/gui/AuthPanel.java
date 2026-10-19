package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** One sign-in screen; connection opens automatically when the form is submitted. */
public final class AuthPanel extends JPanel {
  private final JTextField username = new JTextField(),
      name = new JTextField(),
      email = new JTextField();
  private final JPasswordField password = new JPasswordField(), confirm = new JPasswordField();
  private final Theme.Button loginTab = new Theme.Button("Đăng nhập", "plain"),
      registerTab = new Theme.Button("Đăng ký", "plain"),
      submit = new Theme.Button("VÀO SẢNH CHƠI", "gold"),
      settings = new Theme.Button("Máy chủ", "plain");
  private final JTextArea message = new JTextArea();
  private final JLabel caption = Theme.label("Chào mừng bạn trở lại", 23, Theme.TEXT);
  private final JPanel extra = Theme.transparent(new GridLayout(0, 1, 0, 12));
  private final Theme.Glass form = new Theme.Glass(new BorderLayout(0, 20));
  private boolean register;

  public AuthPanel() {
    setLayout(null);
    setBackground(Theme.BG);
    form.setBorder(new EmptyBorder(28, 32, 26, 32));
    var tabs = Theme.transparent(new GridLayout(1, 2, 10, 0));
    tabs.add(loginTab);
    tabs.add(registerTab);
    var top = Theme.transparent(new BorderLayout(0, 22));
    top.add(tabs, BorderLayout.NORTH);
    top.add(caption, BorderLayout.CENTER);
    form.add(top, BorderLayout.NORTH);
    var fields = Theme.transparent(new GridLayout(0, 1, 0, 12));
    fields.add(row("TÀI KHOẢN", username));
    fields.add(row("MẬT KHẨU", password));
    extra.add(row("NHẬP LẠI MẬT KHẨU", confirm));
    var optional = Theme.transparent(new GridLayout(1, 2, 12, 0));
    optional.add(row("TÊN HIỂN THỊ · TÙY CHỌN", name));
    optional.add(row("EMAIL · TÙY CHỌN", email));
    extra.add(optional);
    extra.setPreferredSize(new Dimension(360, 144));
    var stack = Theme.transparent(new BorderLayout(0, 12));
    stack.add(fields, BorderLayout.NORTH);
    stack.add(extra, BorderLayout.CENTER);
    var middle = Theme.transparent(new BorderLayout());
    middle.add(stack, BorderLayout.NORTH);
    fields.setPreferredSize(new Dimension(360, 144));
    form.add(middle, BorderLayout.CENTER);
    message.setEditable(false);
    message.setFocusable(false);
    message.setLineWrap(true);
    message.setWrapStyleWord(true);
    message.setOpaque(false);
    message.setForeground(Theme.RED);
    message.setFont(Theme.font(12, false));
    message.setPreferredSize(new Dimension(360, 36));
    var bottom = Theme.transparent(new BorderLayout(0, 10));
    bottom.add(message, BorderLayout.NORTH);
    bottom.add(submit, BorderLayout.CENTER);
    form.add(bottom, BorderLayout.SOUTH);
    add(form);
    add(settings);
    for (var f : new JTextField[] {username, password, confirm, name, email}) Theme.field(f);
    password.setToolTipText("Mật khẩu đăng ký từ 8 đến 128 ký tự");
    loginTab.addActionListener(e -> mode(false));
    registerTab.addActionListener(e -> mode(true));
    mode(false);
  }

  private static JPanel row(String label, JTextField field) {
    var p = Theme.transparent(new BorderLayout(0, 7));
    p.add(Theme.label(label, 11, Theme.MUTED), BorderLayout.NORTH);
    p.add(field, BorderLayout.CENTER);
    p.setPreferredSize(new Dimension(170, 66));
    return p;
  }

  private void mode(boolean value) {
    register = value;
    extra.setVisible(value);
    clearPasswords();
    message.setText(" ");
    caption.setText(value ? "Một tài khoản, những cuộc vui" : "Chào mừng bạn trở lại");
    caption.setFont(Theme.font(value ? 20 : 23, true));
    submit.setText(value ? "TẠO TÀI KHOẢN · 1.000 ĐIỂM" : "VÀO SẢNH CHƠI");
    loginTab.putClientProperty("active", !value);
    registerTab.putClientProperty("active", value);
    loginTab.setForeground(value ? Theme.MUTED : Theme.GOLD);
    registerTab.setForeground(value ? Theme.GOLD : Theme.MUTED);
    revalidate();
    repaint();
  }

  @Override
  public void doLayout() {
    int w = Math.min(450, getWidth() - 64), h = register ? 660 : 480;
    int x = getWidth() < 1050 ? (getWidth() - w) / 2 : getWidth() - w - 100;
    form.setBounds(x, Math.max(18, (getHeight() - h) / 2), w, h);
    settings.setBounds(getWidth() - 138, 20, 110, 38);
  }

  @Override
  protected void paintComponent(Graphics gg) {
    super.paintComponent(gg);
    var g = (Graphics2D) gg.create();
    Assets.quality(g);
    Assets.cover(g, Assets.get("backgrounds/auth.png", 1700), getWidth(), getHeight());
    g.setColor(new Color(2, 12, 10, 90));
    g.fillRect(0, 0, getWidth(), getHeight());
    if (getWidth() >= 1050) {
      int x = 85, y = Math.max(115, getHeight() / 2 - 190);
      g.setColor(Theme.GOLD);
      g.setFont(Theme.font(14, true));
      g.drawString("CÙNG BẠN BÈ · CÙNG MỘT BÀN", x, y);
      g.setFont(Theme.font(78, true));
      g.drawString("BA CÂY", x, y + 89);
      g.setColor(Theme.TEXT);
      g.setFont(Theme.font(21, false));
      g.drawString("Ba lá bài. Một cuộc vui.", x, y + 132);
      g.setColor(Theme.MUTED);
      g.setFont(Theme.font(14, false));
      g.drawString("Chọn bàn, sẵn sàng và bắt đầu ván mới.", x, y + 165);
      Card[] cards = {
        new Card(Rank.ACE, Suit.BICH), new Card(Rank.ACE, Suit.CO), new Card(Rank.ACE, Suit.RO)
      };
      for (int i = 0; i < 3; i++) {
        var c = (Graphics2D) g.create();
        c.translate(x + 80 + i * 110, y + 212);
        c.rotate(Math.toRadians((i - 1) * 12), 66, 160);
        c.setColor(new Color(0, 0, 0, 90));
        c.fillRoundRect(5, 8, 132, 185, 12, 12);
        c.drawImage(Assets.card(cards[i]), 0, 0, 132, 185, null);
        c.dispose();
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
    return register;
  }

  public void onSubmit(Runnable r) {
    submit.addActionListener(e -> r.run());
    password.addActionListener(
        e -> {
          if (submit.isEnabled()) r.run();
        });
    confirm.addActionListener(
        e -> {
          if (submit.isEnabled()) r.run();
        });
  }

  public void onSettings(Runnable r) {
    settings.addActionListener(e -> r.run());
  }

  public void clearPasswords() {
    password.setText("");
    confirm.setText("");
  }

  public void completed() {
    mode(false);
    password.requestFocusInWindow();
  }

  public void message(String text) {
    message.setToolTipText(text);
    message.setText(text == null || text.isBlank() ? " " : text);
  }

  public void setBusy(boolean busy) {
    for (var c :
        new JComponent[] {
          username, password, confirm, name, email, loginTab, registerTab, submit, settings
        }) c.setEnabled(!busy);
    submit.setText(
        busy ? "ĐANG XỬ LÝ…" : register ? "TẠO TÀI KHOẢN · 1.000 ĐIỂM" : "VÀO SẢNH CHƠI");
  }
}
