package com.game3cay;

import com.game3cay.controller.ConnectionController;
import com.game3cay.gui.MainFrame;

import javax.swing.SwingUtilities;

public final class RunClient {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame view = new MainFrame();
            new ConnectionController(view);
            view.setVisible(true);
        });
    }
}