package com.game3cay.gui;

import com.game3cay.shared.model.ChangePasswordRequest;
import com.game3cay.shared.model.ProfileResult;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.UpdateProfileRequest;
import java.math.BigDecimal;
import java.util.function.Consumer;
import javax.swing.JFrame;

/** The session UI used by the network controller, independent of its top-level window. */
public interface ClientView {
    JFrame ownerFrame();
    String getHost();
    int getPort();
    String getUsername();
    char[] getPassword();
    char[] getConfirmPassword();
    String getDisplayName();
    String getEmail();
    boolean isRegisterMode();
    void onSubmit(Runnable action);
    void onLogout(Runnable action);
    void onClose(Runnable action);
    void onProfile(Runnable action);
    void onHistory(Runnable action);
    void onLeaderboard(Runnable action);
    void onChangePassword(Consumer<ChangePasswordRequest> action);
    void clearPasswords();
    void registrationCompleted();
    void showSignedOut();
    void showSignedIn(String name, long id);
    void setBalance(BigDecimal value);
    void showLobby();
    void showRoom(RoomSnapshot room, long userId);
    LobbyPanel lobby();
    RoomPanel room();
    void setOperationEnabled(boolean enabled);
    void showMessage(String message);
    boolean confirmLeave();
    void editProfile(ProfileResult profile, boolean editable, Consumer<UpdateProfileRequest> save);
    void dispose();
}
