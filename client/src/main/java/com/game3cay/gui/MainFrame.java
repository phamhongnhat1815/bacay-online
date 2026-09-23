package com.game3cay.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final JTextField hostField =
            new JTextField("127.0.0.1", 12);

    private final JTextField portField =
            new JTextField("8888", 6);

    private final JButton connectButton =
            new JButton("Kết nối");

    private final JButton disconnectButton =
            new JButton("Ngắt kết nối");

    private final JButton sendButton =
            new JButton("Gửi PING thử");

    private final JLabel statusLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();

    public MainFrame() {
        setTitle("BTL-LTM — T01: Kết nối client");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(760, 420);
        setLocationRelativeTo(null);

        JPanel connectionPanel =
                new JPanel(new FlowLayout(FlowLayout.LEFT));

        connectionPanel.add(new JLabel("IP:"));
        connectionPanel.add(hostField);
        connectionPanel.add(new JLabel("Port:"));
        connectionPanel.add(portField);
        connectionPanel.add(connectButton);
        connectionPanel.add(disconnectButton);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        content.add(connectionPanel, BorderLayout.NORTH);
        content.add(new JScrollPane(logArea), BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(content);
        setConnectionState(false, false, "Chưa kết nối");
    }

    public String getHost() {
        return hostField.getText().trim();
    }

    public String getPortText() {
        return portField.getText().trim();
    }

    public void onConnect(Runnable action) {
        connectButton.addActionListener(e -> action.run());
    }

    public void onDisconnect(Runnable action) {
        disconnectButton.addActionListener(e -> action.run());
    }

    public void onSend(Runnable action) {
        sendButton.addActionListener(e -> action.run());
    }

    public void onClose(Runnable action) {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                action.run();
            }
        });
    }

    // Chỉ gọi các hàm cập nhật UI trên EDT.
    public void setConnectionState(
            boolean connected,
            boolean connecting,
            String text
    ) {
        hostField.setEnabled(!connected && !connecting);
        portField.setEnabled(!connected && !connecting);

        connectButton.setEnabled(!connected && !connecting);
        disconnectButton.setEnabled(connected || connecting);
        sendButton.setEnabled(connected);

        statusLabel.setText(text);
    }

    public void appendLog(String text) {
        // Không để vùng log tăng mãi khi chạy thử lâu.
        if (logArea.getDocument().getLength() > 20000) {
            logArea.setText("");
        }

        logArea.append(text + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}