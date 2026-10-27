package com.game3cay;

import com.game3cay.controller.ConnectionController;
import com.game3cay.gui.MainFrame;
import com.game3cay.gui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Điểm khởi chạy ứng dụng Client của game Ba Cây Online.
 * <p>
 * Nhận tham số dòng lệnh tùy chọn:
 * <ul>
 *   <li>{@code args[0]}: Địa chỉ IP / Host của máy chủ (mặc định đọc từ cấu hình).</li>
 *   <li>{@code args[1]}: Cổng TCP của máy chủ (1–65535, mặc định 8888).</li>
 * </ul>
 */
public final class RunClient {

    public static void main(String[] args) {
        // Bật khử răng cưa chữ (Anti-aliasing) trên màn hình độ phân giải cao
        System.setProperty("swing.aatext", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");

        if (args.length > 0 && !args[0].isBlank()) {
            System.setProperty("server.host", args[0].trim());
        }

        if (args.length > 1 && !args[1].isBlank()) {
            try {
                int port = Integer.parseInt(args[1].trim());
                if (port < 1 || port > 65535) {
                    throw new NumberFormatException();
                }
                System.setProperty("server.port", String.valueOf(port));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Cổng kết nối máy chủ phải là số nguyên từ 1 đến 65535.",
                        "Lỗi cấu hình",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
        }

        // Khởi động giao diện người dùng trên luồng Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            MainFrame view = new MainFrame();
            new ConnectionController(view);
            view.setVisible(true);
        });
    }
}
