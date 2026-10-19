package com.game3cay;

import com.game3cay.controller.ConnectionController;
import com.game3cay.gui.MainFrame;
import javax.swing.SwingUtilities;

public final class RunClient {
  public static void main(String[] args) {
    System.setProperty("swing.aatext", "true");
    System.setProperty("awt.useSystemAAFontSettings", "on");
    if (args.length > 0 && !args[0].isBlank()) System.setProperty("server.host", args[0]);
    if (args.length > 1 && !args[1].isBlank()) {
      try {
        int port = Integer.parseInt(args[1]);
        if (port < 1 || port > 65535) throw new NumberFormatException();
        System.setProperty("server.port", args[1]);
      } catch (NumberFormatException e) {
        javax.swing.JOptionPane.showMessageDialog(null, "Cong game phai tu 1 den 65535.");
        return;
      }
    }
    SwingUtilities.invokeLater(
        () -> {
          com.game3cay.gui.Theme.install();
          MainFrame view = new MainFrame();
          new ConnectionController(view);
          view.setVisible(true);
        });
  }
}
