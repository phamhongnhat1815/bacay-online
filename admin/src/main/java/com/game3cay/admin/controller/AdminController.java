package com.game3cay.admin.controller;

import com.game3cay.admin.gui.AdminAuthPanel;
import com.game3cay.admin.gui.AdminMainFrame;
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
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketType;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Controller điều phối toàn bộ luồng mạng TCP và tương tác giao diện người dùng Admin trên luồng Swing EDT.
 */
public final class AdminController {

    private final AdminMainFrame frame;
    private AdminSocketClient client;
    private ScheduledExecutorService poller;
    private LiveRoomInspectorDialog activeInspectorDialog;

    private AuthResult currentAdmin;

    public AdminController(AdminMainFrame frame) {
        this.frame = frame;
        bindEvents();
    }

    private void bindEvents() {
        frame.getAuthPanel().setOnLogin(this::handleLogin);
        frame.setOnLogout(this::handleLogout);

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
        new Thread(() -> {
            try {
                if (client != null) {
                    client.close();
                }

                client = new AdminSocketClient(
                        creds.host(),
                        creds.port(),
                        this::onServerPush,
                        () -> SwingUtilities.invokeLater(this::onDisconnected)
                );
                client.connect();

                String rawPass = new String(creds.password());
                Arrays.fill(creds.password(), '\0');

                Packet authPacket = client.send(PacketType.LOGIN, new AuthRequest(creds.username(), rawPass)).get();

                if (authPacket.getType() == PacketType.AUTH_RESULT && authPacket.getData() instanceof AuthResult auth) {
                    if (auth.success()) {
                        if (!"ADMIN".equalsIgnoreCase(auth.role())) {
                            client.close();
                            SwingUtilities.invokeLater(() -> frame.getAuthPanel().showError("Tài khoản không có quyền Quản trị viên (ADMIN)"));
                            return;
                        }

                        this.currentAdmin = auth;
                        SwingUtilities.invokeLater(() -> {
                            frame.showConsoleScreen(auth.displayName(), auth.role());
                            startAutoPoller();
                            loadDashboardStats();
                            loadUsersPage(new AdminUserFilterRequest("", "ALL", 0, 20));
                            loadRoomsMonitor();
                            loadLedgerPage(0);
                        });
                    } else {
                        client.close();
                        SwingUtilities.invokeLater(() -> frame.getAuthPanel().showError(auth.message() != null ? auth.message() : "Đăng nhập thất bại"));
                    }
                } else if (authPacket.getType() == PacketType.ERROR && authPacket.getData() instanceof Packet.ErrorPayload err) {
                    client.close();
                    SwingUtilities.invokeLater(() -> frame.getAuthPanel().showError(err.message()));
                } else {
                    client.close();
                    SwingUtilities.invokeLater(() -> frame.getAuthPanel().showError("Phản hồi không mong muốn từ máy chủ"));
                }
            } catch (Exception ex) {
                if (client != null) {
                    client.close();
                }
                SwingUtilities.invokeLater(() -> frame.getAuthPanel().showError("Không thể kết nối máy chủ: " + ex.getMessage()));
            }
        }, "admin-login-worker").start();
    }

    private void handleLogout() {
        closeActiveInspector();
        stopAutoPoller();
        if (client != null) {
            client.close();
            client = null;
        }
        currentAdmin = null;
        frame.showAuthScreen();
    }

    private void onDisconnected() {
        closeActiveInspector();
        stopAutoPoller();
        frame.setStatusMessage("Mất kết nối tới máy chủ. Vui lòng kiểm tra lại dịch vụ Server.", true);
    }

    private void onServerPush(Packet packet) {
        // Có thể mở rộng để nhận sự kiện real-time
    }

    // --- Dashboard & Broadcast ---

    public void loadDashboardStats() {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_STATS_REQ, null).thenAccept(packet -> {
            if (packet.getData() instanceof AdminStatsSnapshot stats) {
                SwingUtilities.invokeLater(() -> frame.getDashboardPanel().updateStats(stats));
            }
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> frame.setStatusMessage("Không thể nạp số liệu thống kê: " + ex.getMessage(), true));
            return null;
        });
    }

    private void handleBroadcast(AdminBroadcastRequest req) {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_BROADCAST_REQ, req).thenAccept(packet -> {
            SwingUtilities.invokeLater(() -> {
                if (packet.getType() == PacketType.ACK) {
                    frame.getDashboardPanel().broadcastSentSuccess();
                } else if (packet.getData() instanceof Packet.ErrorPayload err) {
                    frame.getDashboardPanel().broadcastFailed(err.message());
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> frame.getDashboardPanel().broadcastFailed(ex.getMessage()));
            return null;
        });
    }

    // --- Users Management ---

    public void loadUsersPage(AdminUserFilterRequest req) {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_USERS_PAGE_REQ, req).thenAccept(packet -> {
            if (packet.getData() instanceof PageResult<?> pageRes) {
                @SuppressWarnings("unchecked")
                PageResult<AdminUserItem> usersPage = (PageResult<AdminUserItem>) pageRes;
                SwingUtilities.invokeLater(() -> frame.getUsersPanel().updatePage(usersPage));
            }
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> frame.setStatusMessage("Lỗi tải danh sách người dùng: " + ex.getMessage(), true));
            return null;
        });
    }

    private void handleLockUser(AdminLockUserRequest req) {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_LOCK_USER_REQ, req).thenAccept(packet -> {
            SwingUtilities.invokeLater(() -> {
                if (packet.getType() == PacketType.ACK) {
                    JOptionPane.showMessageDialog(frame, "Đã cập nhật trạng thái tài khoản thành công.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    loadUsersPage(new AdminUserFilterRequest("", "ALL", frame.getUsersPanel().getCurrentPage(), 20));
                    loadDashboardStats();
                } else if (packet.getData() instanceof Packet.ErrorPayload err) {
                    JOptionPane.showMessageDialog(frame, "Không thể khóa/mở khóa: " + err.message(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Lỗi kết nối: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
            return null;
        });
    }

    private void handleAdjustBalance(AdminAdjustBalanceRequest req) {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_ADJUST_BALANCE_REQ, req).thenAccept(packet -> {
            SwingUtilities.invokeLater(() -> {
                if (packet.getType() == PacketType.ACK) {
                    JOptionPane.showMessageDialog(frame, "Đã điều chỉnh số dư điểm thành công.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    loadUsersPage(new AdminUserFilterRequest("", "ALL", frame.getUsersPanel().getCurrentPage(), 20));
                    loadDashboardStats();
                    loadLedgerPage(0);
                } else if (packet.getData() instanceof Packet.ErrorPayload err) {
                    JOptionPane.showMessageDialog(frame, "Không thể điều chỉnh điểm: " + err.message(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Lỗi kết nối: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
            return null;
        });
    }

    // --- Rooms Monitor ---

    public void loadRoomsMonitor() {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_ROOMS_MONITOR_REQ, null).thenAccept(packet -> {
            if (packet.getData() instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<AdminRoomDetail> rooms = (List<AdminRoomDetail>) list;
                SwingUtilities.invokeLater(() -> frame.getRoomsPanel().updateRooms(rooms));
            }
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> frame.setStatusMessage("Lỗi giám sát bàn: " + ex.getMessage(), true));
            return null;
        });
    }

    private void handleResetRoom(AdminResetRoomRequest req) {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_FORCE_RESET_ROOM, req).thenAccept(packet -> {
            SwingUtilities.invokeLater(() -> {
                if (packet.getType() == PacketType.ACK) {
                    JOptionPane.showMessageDialog(frame, "Đã giải tán / đặt lại bàn #" + req.roomId() + " thành công.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    loadRoomsMonitor();
                    loadDashboardStats();
                    if (activeInspectorDialog != null && activeInspectorDialog.isVisible()) {
                        refreshRoomInspector(req.roomId());
                    }
                } else if (packet.getData() instanceof Packet.ErrorPayload err) {
                    JOptionPane.showMessageDialog(frame, "Không thể giải tán bàn: " + err.message(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Lỗi kết nối: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
            return null;
        });
    }

    private void openRoomInspector(long roomId) {
        closeActiveInspector();

        activeInspectorDialog = new LiveRoomInspectorDialog(
                frame,
                roomId,
                this::refreshRoomInspector,
                this::handleInspectorResetRoom
        );

        refreshRoomInspector(roomId);
        activeInspectorDialog.setVisible(true);
    }

    private void refreshRoomInspector(long roomId) {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_INSPECT_ROOM_REQ, roomId).thenAccept(packet -> {
            if (packet.getData() instanceof AdminLiveMatchDetail detail) {
                SwingUtilities.invokeLater(() -> {
                    if (activeInspectorDialog != null && activeInspectorDialog.isVisible()) {
                        activeInspectorDialog.updateMatchDetail(detail);
                    }
                });
            }
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                if (activeInspectorDialog != null && activeInspectorDialog.isVisible()) {
                    frame.setStatusMessage("Lỗi tải chi tiết ván: " + ex.getMessage(), true);
                }
            });
            return null;
        });
    }

    private void handleInspectorResetRoom(long roomId) {
        String reason = JOptionPane.showInputDialog(
                activeInspectorDialog,
                "Nhập lý do cưỡng chế giải tán / hủy bàn #" + roomId + ":",
                "Giải Tán Bàn Kẹt",
                JOptionPane.WARNING_MESSAGE
        );

        if (reason != null && !reason.isBlank()) {
            handleResetRoom(new AdminResetRoomRequest(roomId, reason.trim()));
        }
    }

    private void closeActiveInspector() {
        if (activeInspectorDialog != null) {
            activeInspectorDialog.stopTimer();
            activeInspectorDialog.dispose();
            activeInspectorDialog = null;
        }
    }

    // --- Ledger Audit ---

    public void loadLedgerPage(int page) {
        if (client == null || !client.isConnected()) return;

        client.send(PacketType.ADMIN_LEDGER_PAGE_REQ, new PageRequest(page, 20)).thenAccept(packet -> {
            if (packet.getData() instanceof PageResult<?> pageRes) {
                @SuppressWarnings("unchecked")
                PageResult<AdminLedgerItem> ledgerPage = (PageResult<AdminLedgerItem>) pageRes;
                SwingUtilities.invokeLater(() -> frame.getLedgerPanel().updatePage(ledgerPage));
            }
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> frame.setStatusMessage("Lỗi tải sổ cái: " + ex.getMessage(), true));
            return null;
        });
    }

    // --- Auto Poller ---

    private void startAutoPoller() {
        stopAutoPoller();
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "admin-dashboard-poller");
            t.setDaemon(true);
            return t;
        });
        poller.scheduleWithFixedDelay(() -> {
            loadDashboardStats();
            loadRoomsMonitor();
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void stopAutoPoller() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }
}
