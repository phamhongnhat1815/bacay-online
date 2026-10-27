package com.game3cay.admin;

import com.game3cay.admin.controller.AdminController;
import com.game3cay.admin.gui.AdminMainFrame;
import javax.swing.SwingUtilities;

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
