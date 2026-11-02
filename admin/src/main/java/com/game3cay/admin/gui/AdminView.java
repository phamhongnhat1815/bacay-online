package com.game3cay.admin.gui;

import com.game3cay.shared.model.ChangePasswordRequest;
import java.awt.Frame;
import java.util.function.Consumer;

/** The admin panels and navigation used by the controller. */
public interface AdminView {
    AdminAuthPanel getAuthPanel();
    DashboardPanel getDashboardPanel();
    UsersPanel getUsersPanel();
    RoomsPanel getRoomsPanel();
    LedgerPanel getLedgerPanel();
    void setOnLogout(Runnable callback);
    void setOnChangePassword(Consumer<ChangePasswordRequest> callback);
    void showAuthScreen();
    void showConsoleScreen(String name, String role);
    void setStatusMessage(String message, boolean error);
    Frame window();
}
