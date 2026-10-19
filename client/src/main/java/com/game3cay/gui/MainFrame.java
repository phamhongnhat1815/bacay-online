package com.game3cay.gui;

import com.game3cay.shared.model.*;
import com.game3cay.shared.network.RuntimeSettings;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class MainFrame extends JFrame {
  private final AuthPanel auth = new AuthPanel();
  private final LobbyPanel lobby = new LobbyPanel();
  private final RoomPanel room = new RoomPanel();
  private final CardLayout screens = new CardLayout(), pages = new CardLayout();
  private final JPanel root = new JPanel(screens), session, body = Theme.transparent(pages);
  private final JLabel account = Theme.label("", 15, Theme.TEXT),
      balance = Theme.label("0", 23, Theme.GOLD),
      message = Theme.label(" ", 12, Theme.MUTED);
  private final Theme.Button profile = new Theme.Button("Hồ sơ", "plain"),
      history = new Theme.Button("Lịch sử", "plain"),
      leaderboard = new Theme.Button("Bảng xếp hạng", "gold"),
      logout = new Theme.Button("Đăng xuất", "plain"),
      sound = new Theme.Button("Âm thanh: Bật", "plain"),
      help = new Theme.Button("Luật chơi", "plain");
  private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
  private String host =
      System.getProperty(
          "server.host",
          prefs.get(
              "serverHost", RuntimeSettings.text("client.properties", "server.host", "127.0.0.1")));
  private int port =
      Integer.getInteger(
          "server.port",
          prefs.getInt(
              "serverPort",
              RuntimeSettings.integer("client.properties", "server.port", 8888, 1, 65535)));
  private boolean signedIn;
  private Consumer<ChangePasswordRequest> passwordAction = r -> {};

  public MainFrame() {
    super("Ba Cây Online");
    Theme.install();
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    session =
        new JPanel(new BorderLayout()) {
          @Override
          protected void paintComponent(Graphics gg) {
            super.paintComponent(gg);
            var g = (Graphics2D) gg.create();
            Assets.quality(g);
            Assets.cover(g, Assets.get("backgrounds/casino.png", 1700), getWidth(), getHeight());
            g.setColor(new Color(0, 15, 12, 180));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.dispose();
          }
        };
    session.setOpaque(false);
    var header = new JPanel(new BorderLayout(15, 0));
    header.setBackground(new Color(5, 20, 18));
    header.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(98, 91, 57)),
            new EmptyBorder(16, 28, 16, 28)));
    var brand = Theme.transparent(new GridLayout(0, 1, 0, 2));
    var logo = Theme.label("♠  BA CÂY", 25, Theme.GOLD);
    logo.setFont(Theme.font(25, true));
    brand.add(logo);
    brand.add(Theme.label("ONLINE  /  CHƠI CÙNG BẠN BÈ", 9, Theme.MUTED));
    header.add(brand, BorderLayout.WEST);
    var info = Theme.transparent(new GridLayout(0, 1, 0, 3));
    info.add(account);
    var wallet = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
    balance.setFont(Theme.font(23, true));
    wallet.add(balance);
    wallet.add(Theme.label("  điểm", 12, Theme.MUTED));
    info.add(wallet);
    info.setPreferredSize(new Dimension(145, 54));
    var actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    actions.add(info);
    actions.add(Box.createHorizontalStrut(13));
    for (var b : new JButton[] {leaderboard, history, profile, help, sound, logout}) {
      b.setFont(Theme.font(13, true));
      b.setBorder(new EmptyBorder(8, 7, 8, 7));
      b.setPreferredSize(
          new Dimension(
              b == leaderboard ? 140 : b == sound ? 92 : b == logout ? 100 : b == help ? 85 : 78,
              42));
      actions.add(b);
    }
    header.add(actions, BorderLayout.EAST);
    session.add(header, BorderLayout.NORTH);
    body.add(lobby, "LOBBY");
    body.add(room, "ROOM");
    session.add(body, BorderLayout.CENTER);
    var footer = Theme.transparent(new BorderLayout());
    footer.setBorder(new EmptyBorder(6, 28, 8, 28));
    footer.add(message, BorderLayout.CENTER);
    footer.add(Theme.label("BA CÂY ONLINE", 10, Theme.MUTED), BorderLayout.EAST);
    session.add(footer, BorderLayout.SOUTH);
    root.add(auth, "AUTH");
    root.add(session, "SESSION");
    setContentPane(root);
    var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    setMinimumSize(new Dimension(1100, 700));
    setSize(Math.min(1440, screen.width - 24), Math.min(930, screen.height - 24));
    setLocationRelativeTo(null);
    sound.setText(Sound.enabled() ? "Âm: Bật" : "Âm: Tắt");
    sound.addActionListener(
        e -> {
          Sound.toggle();
          sound.setText(Sound.enabled() ? "Âm: Bật" : "Âm: Tắt");
        });
    help.addActionListener(e -> showRules());
    auth.onSettings(this::settings);
    showSignedOut();
  }

  private void settings() {
    var h = new JTextField(host);
    var p = new JTextField(Integer.toString(port));
    Theme.field(h);
    Theme.field(p);
    var f = new JPanel(new GridLayout(0, 1, 0, 8));
    f.add(Theme.label("Địa chỉ máy chạy game", 14, Theme.TEXT));
    f.add(h);
    f.add(Theme.label("Cổng", 14, Theme.TEXT));
    f.add(p);
    if (JOptionPane.showConfirmDialog(
            this, f, "Máy chủ của nhóm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION) {
      try {
        int n = Integer.parseInt(p.getText().trim());
        if (n < 1 || n > 65535 || h.getText().isBlank()) throw new NumberFormatException();
        host = h.getText().trim();
        port = n;
        prefs.put("serverHost", host);
        prefs.putInt("serverPort", port);
        showMessage("Đã lưu máy chủ. Đăng nhập để vào chơi.");
      } catch (NumberFormatException e) {
        showMessage("Nhập địa chỉ máy chủ và cổng 1–65535.");
      }
    }
  }

  public void showRules() {
    JOptionPane.showMessageDialog(
        this,
        "Mỗi người nhận 3 lá và góp mức cược cơ bản của bàn.\n"
            + "Đến lượt, chọn Kiểm tra / Theo, Tố đến, Úp bài hoặc Tất tay.\n"
            + "Tố đến là tổng số điểm bạn muốn góp trong ván.\n"
            + "Mỗi lượt tối đa 30 giây; quá giờ sẽ tự úp bài.\n\n"
            + "Thứ tự bài: Sáp > Liêng > Bồ đội > Điểm.\n"
            + "Sáp: ba lá cùng số. Liêng: ba lá liên tiếp.\n"
            + "Bồ đội: cả ba lá là J, Q hoặc K.\n"
            + "Điểm: lấy hàng đơn vị của tổng; A = 1, 10/J/Q/K = 0.\n"
            + "Cùng loại: so giá trị bài; nếu bằng, so chất Rô > Cơ > Bích > Chuồn.\n\n"
            + "Bấm Sẵn sàng; chủ bàn bấm Bắt đầu khi đủ ít nhất 2 người.\n"
            + "Tài khoản mới được tặng 1.000 điểm giải trí.",
        "Luật bàn Ba Cây",
        JOptionPane.PLAIN_MESSAGE);
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getUsername() {
    return auth.username();
  }

  public char[] getPassword() {
    return auth.password();
  }

  public char[] getConfirmPassword() {
    return auth.confirm();
  }

  public String getDisplayName() {
    return auth.displayName();
  }

  public String getEmail() {
    return auth.email();
  }

  public boolean isRegisterMode() {
    return auth.register();
  }

  public void onSubmit(Runnable r) {
    auth.onSubmit(r);
  }

  public void onLogout(Runnable r) {
    logout.addActionListener(e -> r.run());
  }

  public void onClose(Runnable r) {
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            r.run();
          }
        });
  }

  public void onProfile(Runnable r) {
    profile.addActionListener(e -> r.run());
  }

  public void onHistory(Runnable r) {
    history.addActionListener(e -> r.run());
  }

  public void onLeaderboard(Runnable r) {
    leaderboard.addActionListener(e -> r.run());
  }

  public void onChangePassword(Consumer<ChangePasswordRequest> action) {
    passwordAction = action;
  }

  public void clearPasswords() {
    auth.clearPasswords();
  }

  public void registrationCompleted() {
    auth.completed();
  }

  public void showSignedOut() {
    signedIn = false;
    account.setText("");
    lobby.setRooms(java.util.List.of());
    room.clear();
    clearPasswords();
    screens.show(root, "AUTH");
    auth.setBusy(false);
  }

  public void showSignedIn(String name, long id) {
    signedIn = true;
    account.setText(name);
    account.setToolTipText("Tài khoản #" + id);
    screens.show(root, "SESSION");
  }

  public void setBalance(BigDecimal value) {
    balance.setText(Theme.points(value));
  }

  public void showLobby() {
    room.clear();
    screens.show(root, "SESSION");
    pages.show(body, "LOBBY");
  }

  public void showRoom(RoomSnapshot r, long uid) {
    room.render(r, uid);
    screens.show(root, "SESSION");
    pages.show(body, "ROOM");
  }

  public LobbyPanel lobby() {
    return lobby;
  }

  public RoomPanel room() {
    return room;
  }

  public void setOperationEnabled(boolean value) {
    auth.setBusy(!value);
    lobby.setActionsEnabled(value && signedIn);
    room.setActionsEnabled(value && signedIn);
    for (var b : new JButton[] {profile, history, leaderboard, logout})
      b.setEnabled(value && signedIn);
  }

  public void showMessage(String text) {
    message.setText(text == null || text.isBlank() ? " " : text);
    message.setToolTipText(text);
    auth.message(text);
  }

  public boolean confirmLeave() {
    return !room.game().active()
        || JOptionPane.showConfirmDialog(
                this,
                "Rời bàn lúc này sẽ úp bài. Bạn muốn rời bàn?",
                "Rời bàn",
                JOptionPane.YES_NO_OPTION)
            == JOptionPane.YES_OPTION;
  }

  public void editProfile(ProfileResult p, boolean editable, Consumer<UpdateProfileRequest> save) {
    var dialog = new JDialog(this, "Hồ sơ người chơi", true);
    var panel = new JPanel(new BorderLayout(0, 18));
    panel.setBorder(new EmptyBorder(24, 28, 24, 28));
    var name = new JTextField(p.displayName());
    var email = new JTextField(p.email() == null ? "" : p.email());
    Theme.field(name);
    Theme.field(email);
    name.setEditable(editable);
    email.setEditable(editable);
    var fields = new JPanel(new GridLayout(0, 1, 0, 7));
    fields.add(
        Theme.label(p.username() + "  /  " + Theme.points(p.balance()) + " điểm", 18, Theme.GOLD));
    fields.add(
        Theme.label(
            "Đã chơi "
                + p.totalGames()
                + "  ·  Thắng "
                + p.totalWins()
                + "  ·  Thua "
                + p.totalLosses(),
            13,
            Theme.MUTED));
    fields.add(Theme.label("Tên hiển thị", 12, Theme.MUTED));
    fields.add(name);
    fields.add(Theme.label("Email", 12, Theme.MUTED));
    fields.add(email);
    if (!editable) fields.add(Theme.label("Rời bàn để chỉnh sửa hồ sơ.", 12, Theme.MUTED));
    panel.add(fields, BorderLayout.CENTER);
    var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    var pass = new Theme.Button("Đổi mật khẩu", "plain");
    var close = new Theme.Button("Đóng", "plain");
    var submit = new Theme.Button("Lưu hồ sơ", "gold");
    pass.addActionListener(
        e -> {
          dialog.dispose();
          changePassword();
        });
    close.addActionListener(e -> dialog.dispose());
    submit.setEnabled(editable);
    submit.addActionListener(
        e -> {
          dialog.dispose();
          save.accept(
              new UpdateProfileRequest(name.getText().trim(), email.getText().trim(), null));
        });
    buttons.add(pass);
    buttons.add(close);
    buttons.add(submit);
    panel.add(buttons, BorderLayout.SOUTH);
    dialog.setContentPane(panel);
    dialog.pack();
    dialog.setMinimumSize(new Dimension(510, 410));
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
  }

  private void changePassword() {
    var old = new JPasswordField();
    var next = new JPasswordField();
    var confirm = new JPasswordField();
    var p = new JPanel(new GridLayout(0, 1, 0, 7));
    p.add(Theme.label("Mật khẩu hiện tại", 12, Theme.MUTED));
    p.add(old);
    p.add(Theme.label("Mật khẩu mới (8–128 ký tự)", 12, Theme.MUTED));
    p.add(next);
    p.add(Theme.label("Nhập lại mật khẩu mới", 12, Theme.MUTED));
    p.add(confirm);
    for (var f : new JTextField[] {old, next, confirm}) Theme.field(f);
    if (JOptionPane.showConfirmDialog(
            this, p, "Đổi mật khẩu", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        != JOptionPane.OK_OPTION) return;
    char[] a = old.getPassword(), b = next.getPassword(), c = confirm.getPassword();
    try {
      if (!Arrays.equals(b, c)) {
        showMessage("Hai mật khẩu mới không khớp.");
        return;
      }
      passwordAction.accept(new ChangePasswordRequest(new String(a), new String(b)));
    } finally {
      Arrays.fill(a, '\0');
      Arrays.fill(b, '\0');
      Arrays.fill(c, '\0');
    }
  }
}
