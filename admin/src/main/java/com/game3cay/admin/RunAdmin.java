package com.game3cay.admin;

import com.game3cay.admin.controller.AdminController;
import com.game3cay.admin.gui.AdminMainFrame;
import javax.swing.SwingUtilities;

/**
 * Điểm khởi chạy ứng dụng Quản trị viên (Admin Client) cho hệ thống Ba Cây Online.
 */
public final class RunAdmin {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            AdminMainFrame frame = new AdminMainFrame();
            new AdminController(frame);
            frame.setVisible(true);
        });
    }
}
