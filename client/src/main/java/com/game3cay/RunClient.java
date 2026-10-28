package com.game3cay;

import com.game3cay.controller.ConnectionController;
import com.game3cay.gui.MainFrame;
import com.game3cay.gui.Theme;
import com.game3cay.gui.Assets;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class RunClient {
    public static void main(String[] args) {
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

        // Decode the packaged images on the startup thread, never in the EDT's first paint.
        Assets.preload();
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            MainFrame view = new MainFrame();
            new ConnectionController(view);
            view.setVisible(true);
        });
    }
}
