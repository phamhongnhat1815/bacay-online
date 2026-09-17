package com.game3cay.gui;

import com.game3cay.network.SocketTest;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private JTextField txtHost;
    private JTextField txtPort;
    private JButton btnConnect;

    public MainFrame() {
        setTitle("Game 3 Cây - Client");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Hiển thị chính giữa màn hình
        setLayout(new FlowLayout(FlowLayout.CENTER, 10, 20));

        txtHost = new JTextField("localhost", 10);
        txtPort = new JTextField("8888", 5);
        btnConnect = new JButton("Kết nối Server");

        add(new JLabel("Host:"));
        add(txtHost);
        add(new JLabel("Port:"));
        add(txtPort);
        add(btnConnect);

        // Sự kiện click nút Kết nối
        btnConnect.addActionListener(e -> {
            try {
                String host = txtHost.getText().trim();
                int port = Integer.parseInt(txtPort.getText().trim());

                SocketTest.getInstance().connect(host, port);
                JOptionPane.showMessageDialog(this, "Kết nối thành công tới Server!");

                // Test gửi tin nhắn JSON thử nghiệm
                SocketTest.getInstance().send("{\"type\":\"PING\", \"message\":\"Hello from Swing Client\"}");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Kết nối thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public static void main(String[] args) {
        // Dùng Nimbus Look & Feel tích hợp sẵn trong Java Core
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        // Bật giao diện Swing
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}