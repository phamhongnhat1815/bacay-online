package com.game3cay.admin.controller;

import com.game3cay.admin.gui.AdminAuthPanel;
import com.game3cay.admin.gui.AdminView;
import com.game3cay.admin.gui.LiveRoomInspectorDialog;
import com.game3cay.admin.network.AdminSocketClient;
import com.game3cay.shared.model.AdminAdjustBalanceRequest;
import com.game3cay.shared.model.AdminBroadcastRequest;
import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.AdminLiveMatchDetail;
import com.game3cay.shared.model.AdminLockUserRequest;
import com.game3cay.shared.model.AdminResetRoomRequest;
import com.game3cay.shared.model.AdminRoomDetail;
import com.game3cay.shared.model.AdminStatsSnapshot;
import com.game3cay.shared.model.AdminUserFilterRequest;
import com.game3cay.shared.model.AdminUserItem;
import com.game3cay.shared.model.AuthRequest;
import com.game3cay.shared.model.AuthResult;
import com.game3cay.shared.model.PageRequest;
import com.game3cay.shared.model.PageResult;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketType;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class AdminController {
    private final AdminView frame;
    private AdminSocketClient client;
    private ScheduledExecutorService poller;
    private LiveRoomInspectorDialog activeInspectorDialog;
    private final RoomInspectorRequests inspectorRequests = new RoomInspectorRequests();
    private final AdminRequests requests = new AdminRequests();
    private AuthResult currentAdmin;
    private AdminUserFilterRequest userFilter = new AdminUserFilterRequest("", "ALL", 0, 20);

    public AdminController(AdminView frame) {
        this.frame = frame;
        bindEvents();
    }

    private void bindEvents() {
        frame.getAuthPanel().setOnLogin(this::handleLogin);
        frame.setOnLogout(this::handleLogout);
        frame.setOnChangePassword(req -> send(PacketType.CHANGE_PASSWORD, req, false, packet -> {
            if (packet.getType() == PacketType.ACK) frame.setStatusMessage("Đã đổi mật khẩu quản trị.", false);
            else frame.setStatusMessage(errorMessage(packet), true);
        }, error -> frame.setStatusMessage("Không đổi được mật khẩu: " + error, true)));
        frame.getDashboardPanel().setOnRefresh(this::loadDashboardStats);
        frame.getDashboardPanel().setOnBroadcast(this::handleBroadcast);
        frame.getUsersPanel().setOnSearch(this::loadUsersPage);
        frame.getUsersPanel().setOnLock(this::handleLockUser);
        frame.getUsersPanel().setOnAdjust(this::handleAdjustBalance);
        frame.getRoomsPanel().setOnRefresh(this::loadRoomsMonitor);
        frame.getRoomsPanel().setOnReset(this::handleResetRoom);
        frame.getRoomsPanel().setOnInspect(this::openRoomInspector);
        frame.getLedgerPanel().setOnPageChange(this::loadLedgerPage);
    }

    private void handleLogin(AdminAuthPanel.LoginCredentials creds) {
        closeSession();
        long session = requests.beginSession();
        String password = new String(creds.password());
        Arrays.fill(creds.password(), '\0');
        frame.getAuthPanel().clearPassword();
        AdminSocketClient attempt = new AdminSocketClient(creds.host(), creds.port(),
                packet -> SwingUtilities.invokeLater(() -> onServerPush(session, packet)),
                () -> SwingUtilities.invokeLater(() -> onDisconnected(session)));
        client = attempt;
        new Thread(() -> {
            try {
                attempt.connect();
                Packet packet = attempt.send(PacketType.LOGIN, new AuthRequest(creds.username(), password)).get();
                SwingUtilities.invokeLater(() -> {
                    if (!requests.isCurrent(session)) return;
                    if (packet.getData() instanceof AuthResult auth && auth.success()
                            && "ADMIN".equalsIgnoreCase(auth.role()) && attempt.isConnected()) {
                        currentAdmin = auth;
                        frame.showConsoleScreen(auth.displayName(), auth.role());
                        startAutoPoller(session);
                        loadDashboardStats();
                        loadUsersPage(new AdminUserFilterRequest("", "ALL", 0, 20));
                        loadRoomsMonitor();
                        loadLedgerPage(0);
                    } else {
                        String message = errorMessage(packet);
                        if (packet.getData() instanceof AuthResult auth && auth.success()) {
                            message = "ADMIN".equalsIgnoreCase(auth.role())
                                    ? "Kết nối máy chủ đã bị ngắt. Vui lòng đăng nhập lại."
                                    : "Tài khoản không có quyền Quản trị viên (ADMIN)";
                        }
                        loginFailed(session, message);
                    }
                });
            } catch (Exception error) {
                attempt.close();
                SwingUtilities.invokeLater(() -> loginFailed(session, "Không thể kết nối máy chủ: " + failureMessage(error)));
            }
        }, "admin-login-worker").start();
    }

    private void loginFailed(long session, String message) {
        if (!requests.isCurrent(session)) return;
        closeSession();
        frame.showAuthScreen();
        frame.getAuthPanel().showError(message);
    }

    private void closeSession() {
        requests.endSession();
        closeActiveInspector();
        stopAutoPoller();
        AdminSocketClient previous = client;
        client = null;
        currentAdmin = null;
        userFilter = new AdminUserFilterRequest("", "ALL", 0, 20);
        if (previous != null) previous.close();
    }

    private void handleLogout() {
        closeSession();
        frame.showAuthScreen();
    }

    private void onDisconnected(long session) {
        if (!requests.isCurrent(session) || currentAdmin == null) return;
        loginFailed(session, "Mất kết nối tới máy chủ. Vui lòng đăng nhập lại.");
    }

    private void onServerPush(long session, Packet packet) {
        if (!requests.isCurrent(session)) return;
        if (packet.getType() == PacketType.ERROR && packet.getData() instanceof Packet.ErrorPayload error
                && (error.code() == ErrorCode.USER_BLOCKED || error.code() == ErrorCode.UNAUTHENTICATED)) {
            loginFailed(session, error.message());
        }
    }

    /** Every callback is applied on the EDT, only to the session that issued it. */
    private void send(PacketType type, Object data, boolean latestOnly,
                      Consumer<Packet> onResponse, Consumer<String> onFailure) {
        AdminSocketClient activeClient = client;
        if (activeClient == null || !activeClient.isConnected() || currentAdmin == null) {
            onFailure.accept("Chưa kết nối tới máy chủ");
            return;
        }
        AdminRequests.Ticket ticket = requests.next(type, latestOnly);
        activeClient.send(type, data).whenComplete((packet, error) -> SwingUtilities.invokeLater(() -> {
            if (!requests.accepts(ticket)) return;
            if (error != null) onFailure.accept(failureMessage(error));
            else onResponse.accept(packet);
        }));
    }

    public void loadDashboardStats() {
        send(PacketType.ADMIN_STATS_REQ, null, true, packet -> {
            if (packet.getType() == PacketType.ADMIN_STATS_RESP && packet.getData() instanceof AdminStatsSnapshot stats)
                frame.getDashboardPanel().updateStats(stats);
            else frame.setStatusMessage(errorMessage(packet), true);
        }, error -> frame.setStatusMessage("Không thể nạp số liệu thống kê: " + error, true));
    }

    private void handleBroadcast(AdminBroadcastRequest req) {
        send(PacketType.ADMIN_BROADCAST_REQ, req, false, packet -> {
            if (packet.getType() == PacketType.ACK) frame.getDashboardPanel().broadcastSentSuccess();
            else frame.getDashboardPanel().broadcastFailed(errorMessage(packet));
        }, error -> frame.getDashboardPanel().broadcastFailed(error));
    }

    public void loadUsersPage(AdminUserFilterRequest req) {
        userFilter = req;
        send(PacketType.ADMIN_USERS_PAGE_REQ, req, true, packet -> {
            if (packet.getType() == PacketType.ADMIN_USERS_PAGE_RESP && packet.getData() instanceof PageResult<?> result) {
                if (req.page() > 0 && req.page() >= result.totalPages()) {
                    loadUsersPage(new AdminUserFilterRequest(req.query(), req.statusFilter(),
                            Math.max(0, result.totalPages() - 1), req.pageSize()));
                    return;
                }
                @SuppressWarnings("unchecked") PageResult<AdminUserItem> page = (PageResult<AdminUserItem>) result;
                frame.getUsersPanel().updatePage(page);
            } else frame.setStatusMessage(errorMessage(packet), true);
        }, error -> frame.setStatusMessage("Lỗi tải danh sách người dùng: " + error, true));
    }

    void handleLockUser(AdminLockUserRequest req) {
        send(PacketType.ADMIN_LOCK_USER_REQ, req, false, packet -> {
            frame.getUsersPanel().setActionsBusy(false);
            if (packet.getType() == PacketType.ACK) {
                frame.setStatusMessage("Đã cập nhật trạng thái tài khoản #" + req.userId() + ".", false);
                loadUsersPage(userFilter);
                loadDashboardStats();
                loadRoomsMonitor();
            } else showActionError("Không thể khóa/mở khóa: " + errorMessage(packet));
        }, error -> {
            frame.getUsersPanel().setActionsBusy(false);
            frame.setStatusMessage("Lỗi kết nối: " + error, true);
        });
    }

    private void handleAdjustBalance(AdminAdjustBalanceRequest req) {
        send(PacketType.ADMIN_ADJUST_BALANCE_REQ, req, false, packet -> {
            frame.getUsersPanel().setActionsBusy(false);
            if (packet.getType() == PacketType.ACK) {
                frame.setStatusMessage("Đã điều chỉnh số dư tài khoản #" + req.userId() + ".", false);
                loadUsersPage(userFilter);
                loadDashboardStats();
                loadLedgerPage(0);
            } else showActionError("Không thể điều chỉnh điểm: " + errorMessage(packet));
        }, error -> {
            frame.getUsersPanel().setActionsBusy(false);
            frame.setStatusMessage("Lỗi kết nối: " + error, true);
        });
    }

    public void loadRoomsMonitor() {
        send(PacketType.ADMIN_ROOMS_MONITOR_REQ, null, true, packet -> {
            if (packet.getType() == PacketType.ADMIN_ROOMS_MONITOR_RESP && packet.getData() instanceof List<?> result) {
                @SuppressWarnings("unchecked") List<AdminRoomDetail> rooms = (List<AdminRoomDetail>) result;
                frame.getRoomsPanel().updateRooms(rooms);
            } else frame.setStatusMessage(errorMessage(packet), true);
        }, error -> frame.setStatusMessage("Lỗi giám sát bàn: " + error, true));
    }

    private void handleResetRoom(AdminResetRoomRequest req) {
        send(PacketType.ADMIN_FORCE_RESET_ROOM, req, false, packet -> {
            if (packet.getType() == PacketType.ACK) {
                frame.setStatusMessage("Đã đặt lại bàn #" + req.roomId() + ".", false);
                loadRoomsMonitor();
                loadDashboardStats();
                loadLedgerPage(0);
                if (activeInspectorDialog != null && activeInspectorDialog.isVisible()
                        && activeInspectorDialog.roomId() == req.roomId()) refreshRoomInspector(req.roomId());
            } else showActionError("Không thể giải tán bàn: " + errorMessage(packet));
        }, error -> showActionError("Lỗi kết nối: " + error));
    }

    private void showActionError(String message) {
        frame.setStatusMessage(message, true);
        JOptionPane.showMessageDialog(frame.window(), message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void openRoomInspector(long roomId) {
        closeActiveInspector();
        activeInspectorDialog = new LiveRoomInspectorDialog(frame.window(), roomId,
                this::refreshRoomInspector, this::handleInspectorResetRoom);
        refreshRoomInspector(roomId);
        activeInspectorDialog.setVisible(true);
    }

    private void refreshRoomInspector(long roomId) {
        LiveRoomInspectorDialog target = activeInspectorDialog;
        if (target == null || target.roomId() != roomId) return;
        RoomInspectorRequests.Ticket request = inspectorRequests.next(roomId);
        send(PacketType.ADMIN_INSPECT_ROOM_REQ, roomId, false, packet -> {
            if (activeInspectorDialog != target || !target.isVisible()) return;
            if (packet.getType() == PacketType.ADMIN_INSPECT_ROOM_RESP && packet.getData() instanceof AdminLiveMatchDetail detail) {
                if (inspectorRequests.accepts(request, detail.roomId())) target.updateMatchDetail(detail);
            } else if (inspectorRequests.accepts(request, roomId)) frame.setStatusMessage(errorMessage(packet), true);
        }, error -> {
            if (activeInspectorDialog == target && target.isVisible() && inspectorRequests.accepts(request, roomId))
                frame.setStatusMessage("Lỗi tải chi tiết ván: " + error, true);
        });
    }

    private void handleInspectorResetRoom(long roomId) {
        String reason = JOptionPane.showInputDialog(activeInspectorDialog,
                "Nhập lý do cưỡng chế giải tán / hủy bàn #" + roomId + ":", "Giải Tán Bàn Kẹt", JOptionPane.WARNING_MESSAGE);
        if (reason != null && !reason.isBlank()) handleResetRoom(new AdminResetRoomRequest(roomId, reason.trim()));
    }

    private void closeActiveInspector() {
        inspectorRequests.cancel();
        if (activeInspectorDialog != null) {
            activeInspectorDialog.stopTimer();
            activeInspectorDialog.dispose();
            activeInspectorDialog = null;
        }
    }

    public void loadLedgerPage(int page) {
        send(PacketType.ADMIN_LEDGER_PAGE_REQ, new PageRequest(page, 20), true, packet -> {
            if (packet.getType() == PacketType.ADMIN_LEDGER_PAGE_RESP && packet.getData() instanceof PageResult<?> result) {
                if (page > 0 && page >= result.totalPages()) {
                    loadLedgerPage(Math.max(0, result.totalPages() - 1));
                    return;
                }
                @SuppressWarnings("unchecked") PageResult<AdminLedgerItem> ledger = (PageResult<AdminLedgerItem>) result;
                frame.getLedgerPanel().updatePage(ledger);
            } else frame.setStatusMessage(errorMessage(packet), true);
        }, error -> frame.setStatusMessage("Lỗi tải sổ cái: " + error, true));
    }

    private void startAutoPoller(long session) {
        stopAutoPoller();
        poller = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "admin-dashboard-poller");
            thread.setDaemon(true);
            return thread;
        });
        poller.scheduleWithFixedDelay(() -> SwingUtilities.invokeLater(() -> {
            if (!requests.isCurrent(session)) return;
            loadDashboardStats();
            loadRoomsMonitor();
        }), 5, 5, TimeUnit.SECONDS);
    }

    private void stopAutoPoller() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }

    private static String errorMessage(Packet packet) {
        if (packet.getData() instanceof Packet.ErrorPayload error) return error.message();
        if (packet.getData() instanceof AuthResult auth && auth.message() != null) return auth.message();
        return "Phản hồi không mong muốn từ máy chủ";
    }

    private static String failureMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
