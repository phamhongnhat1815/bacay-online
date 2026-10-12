package com.game3cay.gui;

import com.game3cay.shared.model.RoomSnapshot;
import java.util.List;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

public final class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final JTextField host = new JTextField("127.0.0.1", 12);
    private final JTextField port = new JTextField("8888", 5);

    private final JButton connect = new JButton("Kết nối");
    private final JButton disconnect = new JButton("Ngắt kết nối");
    private final JButton echo = new JButton("Gửi yêu cầu thử");

    private final JLabel status = new JLabel("Chưa kết nối");
    private final JTextArea log = new JTextArea(6, 40);

    private final JTextField username = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final JPasswordField confirm = new JPasswordField(20);
    private final JTextField displayName = new JTextField(20);
    private final JTextField email = new JTextField(20);

    private final JCheckBox register =
            new JCheckBox("Tạo tài khoản mới");

    private final JButton submit = new JButton("Đăng nhập");
    private final JButton logout = new JButton("Đăng xuất");

    private final JLabel account = new JLabel();
    private final JLabel message =
            new JLabel("Kết nối server để tiếp tục.");

    private final JPanel extra =
            new JPanel(new GridLayout(0, 2, 8, 8));

    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);

    private final LobbyPanel lobby = new LobbyPanel();
    private final RoomPanel room = new RoomPanel();

    private final CardLayout sessionCards = new CardLayout();
    private final JPanel sessionScreens = new JPanel(sessionCards);

    public MainFrame() {
        super("Ba Cây — Client");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel connection = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connection.add(new JLabel("IP:"));
        connection.add(host);
        connection.add(new JLabel("Port:"));
        connection.add(port);
        connection.add(connect);
        connection.add(disconnect);
        connection.add(echo);

        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        fields.add(new JLabel("Tên tài khoản:"));
        fields.add(username);
        fields.add(new JLabel("Mật khẩu:"));
        fields.add(password);

        extra.add(new JLabel("Nhập lại mật khẩu:"));
        extra.add(confirm);
        extra.add(new JLabel("Tên hiển thị (tùy chọn):"));
        extra.add(displayName);
        extra.add(new JLabel("Email (tùy chọn):"));
        extra.add(email);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(fields);
        form.add(Box.createVerticalStrut(8));
        form.add(extra);
        form.add(register);
        form.add(submit);

        JPanel authScreen = new JPanel(new BorderLayout());
        authScreen.add(form, BorderLayout.NORTH);

        JPanel sessionHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sessionHeader.add(account);
        sessionHeader.add(logout);

        sessionScreens.add(lobby, "LOBBY");
        sessionScreens.add(room, "ROOM");

        JPanel sessionScreen = new JPanel(new BorderLayout(8, 8));
        sessionScreen.add(sessionHeader, BorderLayout.NORTH);
        sessionScreen.add(sessionScreens, BorderLayout.CENTER);

        screens.add(authScreen, "AUTH");
        screens.add(sessionScreen, "SESSION");

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createTitledBorder("Tài khoản"));
        center.add(screens, BorderLayout.CENTER);
        center.add(message, BorderLayout.SOUTH);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(status, BorderLayout.NORTH);
        bottom.add(new JScrollPane(log), BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        );
        content.add(connection, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(1000, 800);
        setMinimumSize(new Dimension(960, 760));
        setLocationRelativeTo(null);

        register.addActionListener(e -> {
            clearPasswords();
            updateMode();
            showMessage(" ");
        });

        updateMode();
        showSignedOut();
        setConnectionState(false, false, "Chưa kết nối");
        setOperationEnabled(false);
    }

    private void updateMode() {
        extra.setVisible(register.isSelected());
        submit.setText(register.isSelected() ? "Đăng ký" : "Đăng nhập");
        revalidate();
        repaint();
    }

    public String getHost() {
        return host.getText().trim();
    }

    public String getPortText() {
        return port.getText().trim();
    }

    public String getUsername() {
        return username.getText().trim();
    }

    public char[] getPassword() {
        return password.getPassword();
    }

    public char[] getConfirmPassword() {
        return confirm.getPassword();
    }

    public String getDisplayName() {
        return displayName.getText().trim();
    }

    public String getEmail() {
        return email.getText().trim();
    }

    public boolean isRegisterMode() {
        return register.isSelected();
    }

    public void onConnect(Runnable action) {
        connect.addActionListener(e -> action.run());
    }

    public void onDisconnect(Runnable action) {
        disconnect.addActionListener(e -> action.run());
    }

    public void onSend(Runnable action) {
        echo.addActionListener(e -> action.run());
    }

    public void onSubmit(Runnable action) {
        submit.addActionListener(e -> action.run());
    }

    public void onLogout(Runnable action) {
        logout.addActionListener(e -> action.run());
    }

    public void onClose(Runnable action) {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                action.run();
            }
        });
    }

    // Controller gọi các hàm cập nhật giao diện trên EDT.
    public void setConnectionState(
            boolean connected,
            boolean connecting,
            String text
    ) {
        host.setEnabled(!connected && !connecting);
        port.setEnabled(!connected && !connecting);

        connect.setEnabled(!connected && !connecting);
        disconnect.setEnabled(connected || connecting);
        echo.setEnabled(connected);

        status.setText(text);
    }

    public void setAuthEnabled(boolean enabled) {
        for (JComponent component : new JComponent[] {
                username, password, confirm, displayName,
                email, register, submit, logout
        }) {
            component.setEnabled(enabled);
        }
    }

    public void clearPasswords() {
        password.setText("");
        confirm.setText("");
    }

    public void showSignedOut() {
        account.setText("");
        lobby.setRooms(List.of());
        room.clear();
        clearPasswords();
        cards.show(screens, "AUTH");
    }

    public void showSignedIn(String name, long userId) {
        account.putClientProperty("html.disable", Boolean.TRUE);
        account.setText("Đã đăng nhập: " + name + " — ID: " + userId);
        cards.show(screens, "SESSION");
    }

    public LobbyPanel lobby() {
        return lobby;
    }

    public RoomPanel room() {
        return room;
    }

    public void showLobby() {
        room.clear();
        cards.show(screens, "SESSION");
        sessionCards.show(sessionScreens, "LOBBY");
    }

    public void showRoom(RoomSnapshot snapshot, long myId) {
        room.render(snapshot, myId);
        cards.show(screens, "SESSION");
        sessionCards.show(sessionScreens, "ROOM");
    }

    public void setOperationEnabled(boolean enabled) {
        setAuthEnabled(enabled);
        lobby.setActionsEnabled(enabled);
        room.setActionsEnabled(enabled);
    }

    public void registrationCompleted() {
        register.setSelected(false);
        updateMode();
        password.requestFocusInWindow();
    }

    public void showMessage(String text) {
        message.putClientProperty("html.disable", Boolean.TRUE);
        message.setText(text);
    }

    public void appendLog(String text) {
        if (log.getDocument().getLength() > 20000) {
            log.setText("");
        }

        log.append(text + System.lineSeparator());
        log.setCaretPosition(log.getDocument().getLength());
    }
}