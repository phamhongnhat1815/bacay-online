package com.game3cay.gui;

import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.GameActionRequest;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoundResult;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.io.Serial;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * GamePanel encapsulates the in-room playing board, action control toolbar
 * (Call, Raise, All-in, Fold, Card Visibility Toggle), and the waiting-for-round overlay.
 */
public final class GamePanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Preferences PREFS = Preferences.userNodeForPackage(GamePanel.class);
    private final JCheckBox autoReadyCheck = new JCheckBox("Tự động sẵn sàng", PREFS.getBoolean("auto_ready", false));
    private JButton hostReadyButton;
    private final Timer autoReadyTimer = new Timer(1200, e -> triggerAutoReady());

    private final GameTablePanel table = new GameTablePanel();
    private final JLabel status = Theme.label("Chờ mọi người sẵn sàng", 14, Theme.MUTED);
    private final JTextField raiseTo = new JTextField("20", 5);

    private final Theme.Button call = new Theme.Button("Theo", "gold");
    private final Theme.Button raise = new Theme.Button("Tố đến", "gold");
    private final Theme.Button fold = new Theme.Button("Úp bài", "plain");
    private final Theme.Button allIn = new Theme.Button("Tất tay", "red");
    private final Theme.Button eye = new Theme.Button("Ẩn bài", "plain");
    private final Theme.Button squeezeBtn = new Theme.Button("🖐 Nặn bài", "navy");
    private final Theme.Button emoteBtn = new Theme.Button("😊 Biểu cảm", "plain");
    private Consumer<String> chatSender;

    private final Theme.Button betPlus1 = new Theme.Button("+1x", "plain");
    private final Theme.Button betPlus2 = new Theme.Button("+2x", "plain");
    private final Theme.Button betHalfPot = new Theme.Button("1/2 Pot", "plain");
    private final Theme.Button betPot = new Theme.Button("Pot", "plain");
    private final Theme.Button betMax = new Theme.Button("Max", "plain");
    private final JPanel quickBetsPanel = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 6, 2));

    private GameStateSnapshot game;
    private PlayerStateSnapshot mine;
    private BetStateSnapshot bets;
    private RoundResult result;
    private RoomSnapshot room;
    private String roundId;
    private long myId;
    private boolean enabled;
    private boolean hidden;

    private final JPanel actionBar = Theme.transparent(new BorderLayout(10, 6));
    private final JPanel controls = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 9, 0));
    private final Theme.Glass waitingPanel = new Theme.Glass(new BorderLayout(0, 8));
    private final JLabel waitingTitle = Theme.label("SẴN SÀNG CHO VÁN MỚI", 15, Theme.GOLD);
    private final JLabel waitingBet = Theme.label("", 13, Theme.TEXT);
    private final JLabel waitingCount = Theme.label("", 12, Theme.MUTED);
    private final JPanel waitingActions = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 8, 0));

    private long resultDeadline;
    private final Timer resultTimer = new Timer(100, e -> updateResultClock());

    public GamePanel() {
        super(new BorderLayout());
        setOpaque(false);
        add(table, BorderLayout.CENTER);

        actionBar.setBorder(new EmptyBorder(7, 16, 10, 16));
        actionBar.add(status, BorderLayout.NORTH);

        waitingPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        var waitingLabels = Theme.transparent(new GridLayout(0, 1, 0, 4));
        for (var label : new JLabel[] {waitingTitle, waitingBet, waitingCount}) {
            label.setHorizontalAlignment(SwingConstants.CENTER);
            waitingLabels.add(label);
        }
        waitingTitle.setFont(Theme.font(15, true));
        waitingPanel.add(waitingLabels, BorderLayout.CENTER);
        waitingPanel.add(waitingActions, BorderLayout.SOUTH);
        table.setWaitingOverlay(waitingPanel);

        // Row 1: Các nút hành động chính
        call.setToolTipText("Theo cược (Phím tắt: Space hoặc C)");
        fold.setToolTipText("Úp bài bỏ cuộc (Phím tắt: F)");
        allIn.setToolTipText("Tất tay toàn bộ số dư (Phím tắt: A)");
        raise.setToolTipText("Xác nhận tố đến mức đã nhập (Phím tắt: R hoặc Enter)");
        eye.setToolTipText("Ẩn hoặc hiện ba lá bài của bạn (Phím tắt: H)");
        squeezeBtn.setToolTipText("Mở chế độ Nặn bài để hé mở điểm số (Phím tắt: S)");
        squeezeBtn.addActionListener(e -> openCardSqueeze());
        emoteBtn.setToolTipText("Gửi biểu cảm / Sticker tương tác lên avatar");
        emoteBtn.addActionListener(e -> {
            EmotePopup popup = new EmotePopup(emote -> {
                if (chatSender != null) {
                    chatSender.accept(emote.toChatText());
                }
            });
            popup.showAt(emoteBtn, 0, -popup.getPreferredSize().height - 5);
        });

        table.setOnSendEmote(emote -> {
            if (chatSender != null) {
                chatSender.accept(emote.toChatText());
            }
        });

        for (var component : new JComponent[] {eye, squeezeBtn, emoteBtn, fold, call, allIn, raiseTo, raise}) {
            controls.add(component);
        }
        Theme.field(raiseTo);
        raiseTo.setPreferredSize(new Dimension(90, 43));
        actionBar.add(controls, BorderLayout.CENTER);

        // Row 2: Cụm nút cược nhanh
        quickBetsPanel.add(Theme.label("Tố nhanh:", 11, Theme.MUTED));
        for (var b : new Theme.Button[] {betPlus1, betPlus2, betHalfPot, betPot, betMax}) {
            b.setPreferredSize(new Dimension(68, 28));
            b.setFont(Theme.font(11, true));
            quickBetsPanel.add(b);
        }
        betPlus1.setToolTipText("Tăng thêm 1 lần cược sàn");
        betPlus2.setToolTipText("Tăng thêm 2 lần cược sàn");
        betHalfPot.setToolTipText("Tố thêm 1/2 tổng quỹ pot");
        betPot.setToolTipText("Tố thêm 100% tổng quỹ pot");
        betMax.setToolTipText("Tố mức tối đa có thể");

        betPlus1.addActionListener(e -> applyQuickBetMultiplier(1));
        betPlus2.addActionListener(e -> applyQuickBetMultiplier(2));
        betHalfPot.addActionListener(e -> applyQuickBetPotFraction(0.5));
        betPot.addActionListener(e -> applyQuickBetPotFraction(1.0));
        betMax.addActionListener(e -> applyQuickBetMax());

        actionBar.add(quickBetsPanel, BorderLayout.SOUTH);
        add(actionBar, BorderLayout.SOUTH);

        eye.addActionListener(e -> {
            hidden = !hidden;
            eye.setText(hidden ? "Xem bài" : "Ẩn bài");
            table.hideCards(hidden);
        });

        setupKeyBindings();
        clear();
    }

    /**
     * Attaches ready and start game buttons to the waiting overlay.
     *
     * @param ready ready toggle button
     * @param start host start button
     */
    public void setWaitingControls(JButton ready, JButton start) {
        this.hostReadyButton = ready;
        waitingActions.removeAll();
        waitingActions.add(ready);
        waitingActions.add(start);

        autoReadyCheck.setFont(Theme.font(12, false));
        autoReadyCheck.setForeground(Theme.TEXT);
        autoReadyCheck.setOpaque(false);
        autoReadyCheck.setFocusPainted(false);
        autoReadyCheck.setVisible(false); // Hệ thống máy chủ đã tự động sẵn sàng mặc định 100%
        autoReadyCheck.addActionListener(e -> {
            PREFS.putBoolean("auto_ready", autoReadyCheck.isSelected());
            checkAutoReady();
        });
        waitingActions.add(autoReadyCheck);
        waitingActions.revalidate();
    }

    /**
     * Updates the room snapshot and player identity.
     *
     * @param r   room state snapshot
     * @param uid client's user ID
     */
    public void room(RoomSnapshot r, long uid) {
        this.room = r;
        if ("WAITING".equals(r.status()) && result != null) {
            finishResult();
        }
        this.myId = uid;
        table.room(r, uid);
        render();
    }

    /**
     * Registers listener callback for player bet actions.
     *
     * @param action callback consumer
     */
    public void onAction(Consumer<GameActionRequest> action) {
        call.addActionListener(e -> action.accept(new GameActionRequest(roundId, BetAction.CALL, null)));
        fold.addActionListener(e -> action.accept(new GameActionRequest(roundId, BetAction.FOLD, null)));
        allIn.addActionListener(e -> action.accept(new GameActionRequest(roundId, BetAction.ALL_IN, null)));
        raise.addActionListener(e -> {
            try {
                var raiseAmount = new BigDecimal(raiseTo.getText().trim());
                if (raiseAmount.signum() <= 0 || raiseAmount.scale() > 2 || raiseAmount.precision() > 15) {
                    throw new NumberFormatException();
                }
                if (bets != null && raiseAmount.compareTo(bets.currentHighBet()) <= 0) {
                    status.setText("Mức tố phải cao hơn " + Theme.points(bets.currentHighBet()) + " điểm.");
                    return;
                }
                if (mine != null && raiseAmount.compareTo(mine.myBalance().add(mine.myCurrentBet())) > 0) {
                    status.setText("Bạn không đủ điểm cho mức tố này.");
                    return;
                }
                action.accept(new GameActionRequest(roundId, BetAction.RAISE, raiseAmount));
            } catch (NumberFormatException ex) {
                status.setText("Nhập mức tố bằng số, tối đa 2 chữ số thập phân.");
            }
        });
    }

    /**
     * Resets state for a newly started game round.
     *
     * @param id  round identifier
     * @param uid player user id
     */
    public void start(String id, long uid) {
        resultTimer.stop();
        this.roundId = id;
        this.myId = uid;
        this.game = null;
        this.mine = null;
        this.bets = null;
        this.result = null;
        this.hidden = false;
        eye.setText("Ẩn bài");
        table.clearRound();
        Sound.play("deal");
        render();
    }

    /**
     * Checks if this panel accepts events for the given round ID.
     */
    public boolean accepts(String id) {
        return id != null && id.equals(roundId);
    }

    /**
     * Updates round game state and plays turn notification sound when active.
     */
    public void game(GameStateSnapshot value) {
        if (accepts(value.roundId()) && (game == null || value.stateVersion() > game.stateVersion())) {
            boolean turn = value.currentTurnUserId() != null && value.currentTurnUserId() == myId;
            boolean prior = game != null && game.currentTurnUserId() != null && game.currentTurnUserId() == myId;
            this.game = value;
            table.state(value);
            if (turn && !prior) {
                Sound.play("your_turn");
            }
            render();
        }
    }

    /**
     * Updates private hand and balance snapshot for the local player.
     */
    public void mine(PlayerStateSnapshot value) {
        if (accepts(value.roundId()) && (mine == null || value.stateVersion() >= mine.stateVersion())) {
            this.mine = value;
            table.mine(value);
            render();
        }
    }

    /**
     * Updates betting state snapshot and recalculates default raise target.
     * Phát âm thanh gõ chip realtime khi các người chơi khác Tố, Tất tay hoặc Theo cược.
     */
    public void bets(BetStateSnapshot value) {
        if (accepts(value.roundId()) && (bets == null || value.stateVersion() >= bets.stateVersion())) {
            if (bets != null && (value.mainPot().compareTo(bets.mainPot()) > 0
                    || value.currentHighBet().compareTo(bets.currentHighBet()) > 0)) {
                Sound.playChipRattle();
            }
            this.bets = value;
            table.bets(value);
            BigDecimal step = (room == null) ? BigDecimal.TEN : room.betAmount();
            raiseTo.setText(value.currentHighBet().add(step).stripTrailingZeros().toPlainString());
            render();
        }
    }

    /**
     * Displays round result, schedules countdown timer, and plays audio cue.
     */
    public void result(RoundResult value) {
        if (!accepts(value.roundId()) || result != null) {
            return;
        }
        this.result = value;
        this.resultDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RoundResult.DISPLAY_SECONDS);
        resultTimer.restart();
        table.result(value);

        var me = value.playerResults().stream()
                .filter(p -> p.userId() == myId)
                .findFirst()
                .orElse(null);
        if (me != null && !"CANCELLED".equals(value.roundStatus())) {
            Sound.play(me.profit().signum() >= 0 ? "win" : "lose");
        }
        render();
    }

    private void updateResultClock() {
        if (result == null) {
            resultTimer.stop();
            return;
        }
        if (System.nanoTime() >= resultDeadline) {
            finishResult();
        }
        render();
    }

    private void finishResult() {
        resultTimer.stop();
        this.roundId = null;
        this.game = null;
        this.mine = null;
        this.bets = null;
        this.result = null;
        this.hidden = false;
        eye.setText("Ẩn bài");
        table.clearRound();
    }

    public void setActionsEnabled(boolean value) {
        this.enabled = value;
        render();
    }

    public boolean active() {
        return game != null && result == null && "BETTING".equals(game.phase());
    }

    private void render() {
        boolean turn = enabled
                && active()
                && game.currentTurnUserId() != null
                && game.currentTurnUserId() == myId
                && mine != null
                && bets != null
                && !mine.folded()
                && !mine.allIn();

        BigDecimal due = (bets == null || mine == null)
                ? BigDecimal.ZERO
                : bets.currentHighBet().subtract(mine.myCurrentBet()).max(BigDecimal.ZERO);

        call.setText(due.signum() == 0
                ? "Kiểm tra"
                : "Theo " + Theme.points(mine == null ? due : due.min(mine.myBalance())));
        call.setEnabled(turn);
        fold.setEnabled(turn);
        allIn.setEnabled(turn);
        raise.setEnabled(turn && mine.myBalance().add(mine.myCurrentBet()).compareTo(bets.currentHighBet()) > 0);
        raiseTo.setEnabled(raise.isEnabled());
        eye.setEnabled(mine != null);

        String text;
        if (result != null) {
            var me = result.playerResults().stream()
                    .filter(p -> p.userId() == myId)
                    .findFirst()
                    .orElse(null);

            if ("CANCELLED".equals(result.roundStatus())) {
                text = "Ván đã hủy. Điểm được hoàn lại.";
            } else if (me == null) {
                text = "Ván đã kết thúc.";
            } else if (me.profit().signum() > 0) {
                text = "Bạn thắng +" + Theme.points(me.profit()) + " điểm!";
            } else if (me.profit().signum() < 0) {
                text = "Ván này: " + Theme.points(me.profit()) + " điểm.";
            } else {
                text = "Hòa ván · Điểm không thay đổi.";
            }

            long seconds = Math.max(0, (resultDeadline - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
            text += "  Kết quả tự ẩn sau " + seconds + " giây. Sau đó bạn quyết định có chơi tiếp.";
        } else if (active()) {
            if (mine == null) {
                text = "Bạn đang xem ván chơi.";
            } else if (mine.folded()) {
                text = "Bạn đã úp bài. Chờ kết quả ván này.";
            } else if (mine.allIn()) {
                text = "Bạn đã tất tay. Chờ so bài.";
            } else if (game.currentTurnUserId() != null && game.currentTurnUserId() == myId) {
                text = "Đến lượt bạn · Chọn hành động trong 30 giây";
            } else {
                String name = (room == null)
                        ? "người chơi"
                        : room.players().stream()
                                .filter(p -> Objects.equals(p.userId(), game.currentTurnUserId()))
                                .map(RoomSnapshot.PlayerSlot::displayName)
                                .findFirst()
                                .orElse("người chơi");
                text = "Chờ " + name + " quyết định…";
            }
        } else {
            if (room != null && room.countdownSeconds() > 0) {
                text = "⏳ Tự động bắt đầu ván mới sau " + room.countdownSeconds() + " giây... (Chủ bàn có thể bấm Bắt đầu ngay)";
            } else if (room != null && room.players().size() < 2) {
                text = "Mời thêm một người vào bàn để bắt đầu.";
            } else {
                text = "Tất cả đã sẵn sàng. Chờ bắt đầu ván mới.";
            }
        }

        if (result == null && room != null && "RESULT".equals(room.status())) {
            text = "Đang chuyển sang bàn chờ. Bạn sẽ chọn sẵn sàng cho ván tiếp theo.";
        }

        controls.setVisible(roundId != null && result == null);
        quickBetsPanel.setVisible(roundId != null && result == null);

        boolean canRaise = raise.isEnabled();
        betPlus1.setEnabled(canRaise);
        betPlus2.setEnabled(canRaise);
        betHalfPot.setEnabled(canRaise);
        betPot.setEnabled(canRaise);
        betMax.setEnabled(canRaise);

        boolean waiting = room != null && "WAITING".equals(room.status()) && result == null && roundId == null;
        waitingPanel.setVisible(waiting);

        if (waiting) {
            checkAutoReady();
            var me = room.players().stream().filter(p -> p.userId() == myId).findFirst().orElse(null);
            boolean spectator = me != null && "SPECTATOR".equals(me.status());
            if (spectator) {
                waitingTitle.setText("BẠN ĐANG XEM");
                waitingTitle.setForeground(Theme.MUTED);
            } else if (room.countdownSeconds() > 0) {
                waitingTitle.setText("⏳ VÁN MỚI BẮT ĐẦU SAU " + room.countdownSeconds() + " GIÂY");
                waitingTitle.setForeground(Theme.GOLD);
            } else if (me != null && me.ready()) {
                waitingTitle.setText("BẠN ĐÃ SẴN SÀNG");
                waitingTitle.setForeground(Theme.GREEN);
            } else {
                waitingTitle.setText("SẴN SÀNG CHO VÁN MỚI");
                waitingTitle.setForeground(Theme.GOLD);
            }
            waitingBet.setText("Cược cơ bản: " + Theme.points(room.betAmount()) + " điểm / người");

            var eligible = room.players().stream().filter(p -> "PLAYING".equals(p.status())).toList();
            long readyCount = eligible.stream().filter(RoomSnapshot.PlayerSlot::ready).count();
            if (spectator) {
                waitingCount.setText("Chưa đủ điểm để tham gia ván mới.");
            } else if (room.countdownSeconds() > 0) {
                waitingCount.setText("Mọi người đều đã sẵn sàng! Tự động chia bài sau " + room.countdownSeconds() + "s");
            } else if (eligible.size() < room.minPlayers()) {
                waitingCount.setText("Đang chờ thêm người vào bàn... (Tối thiểu " + room.minPlayers() + " người)");
            } else {
                waitingCount.setText(readyCount + " / " + eligible.size() + " người đã sẵn sàng");
            }
        }

        status.setText(text);
        status.setForeground(turn ? Theme.GOLD : Theme.MUTED);
    }

    private void applyQuickBetMultiplier(int multiplier) {
        if (bets == null || mine == null) return;
        BigDecimal step = (room == null) ? BigDecimal.TEN : room.betAmount();
        BigDecimal maxBet = mine.myBalance().add(mine.myCurrentBet());
        BigDecimal target = bets.currentHighBet().add(step.multiply(BigDecimal.valueOf(multiplier))).min(maxBet);
        raiseTo.setText(target.stripTrailingZeros().toPlainString());
        Sound.playChip();
    }

    private void applyQuickBetPotFraction(double fraction) {
        if (bets == null || mine == null) return;
        BigDecimal step = (room == null) ? BigDecimal.TEN : room.betAmount();
        BigDecimal pot = bets.mainPot();
        BigDecimal add = pot.multiply(BigDecimal.valueOf(fraction)).setScale(2, java.math.RoundingMode.HALF_UP);
        if (add.compareTo(step) < 0) {
            add = step;
        }
        BigDecimal maxBet = mine.myBalance().add(mine.myCurrentBet());
        BigDecimal target = bets.currentHighBet().add(add).min(maxBet);
        raiseTo.setText(target.stripTrailingZeros().toPlainString());
        Sound.playChip();
    }

    private void applyQuickBetMax() {
        if (mine == null) return;
        BigDecimal maxBet = mine.myBalance().add(mine.myCurrentBet());
        raiseTo.setText(maxBet.stripTrailingZeros().toPlainString());
        Sound.playChip();
    }

    private void checkAutoReady() {
        if (!autoReadyCheck.isSelected()) {
            autoReadyTimer.stop();
            return;
        }
        if (room != null && "WAITING".equals(room.status()) && hostReadyButton != null && hostReadyButton.isEnabled()) {
            if ("Sẵn sàng".equals(hostReadyButton.getText())) {
                autoReadyTimer.restart();
            }
        }
    }

    private void triggerAutoReady() {
        autoReadyTimer.stop();
        if (autoReadyCheck.isSelected() && room != null && "WAITING".equals(room.status())
                && hostReadyButton != null && hostReadyButton.isEnabled()
                && "Sẵn sàng".equals(hostReadyButton.getText())) {
            hostReadyButton.doClick();
        }
    }

    private void setupKeyBindings() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        bindKey(im, am, "ACTION_CALL_SPACE", KeyEvent.VK_SPACE, () -> {
            if (!raiseTo.hasFocus() && call.isEnabled()) call.doClick();
        });
        bindKey(im, am, "ACTION_CALL_C", KeyEvent.VK_C, () -> {
            if (!raiseTo.hasFocus() && call.isEnabled()) call.doClick();
        });
        bindKey(im, am, "ACTION_FOLD_F", KeyEvent.VK_F, () -> {
            if (!raiseTo.hasFocus() && fold.isEnabled()) fold.doClick();
        });
        bindKey(im, am, "ACTION_ALLIN_A", KeyEvent.VK_A, () -> {
            if (!raiseTo.hasFocus() && allIn.isEnabled()) allIn.doClick();
        });
        bindKey(im, am, "ACTION_RAISE_R", KeyEvent.VK_R, () -> {
            if (!raiseTo.hasFocus() && raise.isEnabled()) raise.doClick();
        });
        bindKey(im, am, "ACTION_EYE_H", KeyEvent.VK_H, () -> {
            if (!raiseTo.hasFocus() && eye.isEnabled()) eye.doClick();
        });
        bindKey(im, am, "ACTION_SQUEEZE_S", KeyEvent.VK_S, () -> {
            if (!raiseTo.hasFocus() && squeezeBtn.isEnabled() && squeezeBtn.isVisible()) openCardSqueeze();
        });

        raiseTo.addActionListener(e -> {
            if (raise.isEnabled()) raise.doClick();
        });
    }

    private void bindKey(InputMap im, ActionMap am, String name, int keyCode, Runnable action) {
        im.put(KeyStroke.getKeyStroke(keyCode, 0), name);
        am.put(name, new AbstractAction() {
            @Serial
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    public GameTablePanel table() {
        return table;
    }

    public void setChatSender(Consumer<String> sender) {
        this.chatSender = sender;
    }

    public void openCardSqueeze() {
        if (mine == null || mine.myCards() == null || mine.myCards().size() < 3) {
            return;
        }
        var owner = javax.swing.SwingUtilities.getWindowAncestor(this);
        java.awt.Frame parentFrame = (owner instanceof java.awt.Frame f) ? f : null;
        new CardSqueezeDialog(parentFrame, mine.myCards(), () -> {
            hidden = false;
            eye.setText("Ẩn bài");
            table.hideCards(false);
        }).setVisible(true);
    }

    /**
     * Clears all state and resets table visuals.
     */
    public void clear() {
        resultTimer.stop();
        autoReadyTimer.stop();
        this.roundId = null;
        this.game = null;
        this.mine = null;
        this.bets = null;
        this.result = null;
        this.room = null;
        this.hidden = false;
        this.enabled = false;
        table.clear();
        render();
    }
}
