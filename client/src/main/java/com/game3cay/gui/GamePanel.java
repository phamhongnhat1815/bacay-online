package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class GamePanel extends JPanel {
  private final GameTablePanel table = new GameTablePanel();
  private final JLabel status = Theme.label("Chờ mọi người sẵn sàng", 14, Theme.MUTED);
  private final JTextField raiseTo = new JTextField("20", 5);
  private final Theme.Button call = new Theme.Button("Theo", "green"),
      raise = new Theme.Button("Tố đến", "gold"),
      fold = new Theme.Button("Úp bài", "plain"),
      allIn = new Theme.Button("Tất tay", "red"),
      eye = new Theme.Button("Ẩn bài", "plain");
  private GameStateSnapshot game;
  private PlayerStateSnapshot mine;
  private BetStateSnapshot bets;
  private RoundResult result;
  private RoomSnapshot room;
  private String roundId;
  private long myId;
  private boolean enabled, hidden;
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
    for (var c : new JComponent[] {eye, fold, call, allIn, raiseTo, raise}) controls.add(c);
    Theme.field(raiseTo);
    raiseTo.setPreferredSize(new Dimension(90, 43));
    actionBar.add(controls, BorderLayout.CENTER);
    add(actionBar, BorderLayout.SOUTH);
    eye.setToolTipText("Ẩn hoặc hiện ba lá bài của bạn trên máy này");
    eye.addActionListener(
        e -> {
          hidden = !hidden;
          eye.setText(hidden ? "Xem bài" : "Ẩn bài");
          table.hideCards(hidden);
        });
    clear();
  }

  public void setWaitingControls(JButton ready, JButton start) {
    waitingActions.removeAll();
    waitingActions.add(ready);
    waitingActions.add(start);
    waitingActions.revalidate();
  }

  public void room(RoomSnapshot r, long uid) {
    room = r;
    if ("WAITING".equals(r.status()) && result != null) finishResult();
    myId = uid;
    table.room(r, uid);
    render();
  }

  public void onAction(Consumer<GameActionRequest> action) {
    call.addActionListener(
        e -> action.accept(new GameActionRequest(roundId, BetAction.CALL, null)));
    fold.addActionListener(
        e -> action.accept(new GameActionRequest(roundId, BetAction.FOLD, null)));
    allIn.addActionListener(
        e -> action.accept(new GameActionRequest(roundId, BetAction.ALL_IN, null)));
    raise.addActionListener(
        e -> {
          try {
            var n = new BigDecimal(raiseTo.getText().trim());
            if (n.signum() <= 0 || n.scale() > 2 || n.precision() > 15)
              throw new NumberFormatException();
            if (bets != null && n.compareTo(bets.currentHighBet()) <= 0) {
              status.setText(
                  "Mức tố phải cao hơn " + Theme.points(bets.currentHighBet()) + " điểm.");
              return;
            }
            if (mine != null && n.compareTo(mine.myBalance().add(mine.myCurrentBet())) > 0) {
              status.setText("Bạn không đủ điểm cho mức tố này.");
              return;
            }
            action.accept(new GameActionRequest(roundId, BetAction.RAISE, n));
          } catch (NumberFormatException ex) {
            status.setText("Nhập mức tố bằng số, tối đa 2 chữ số thập phân.");
          }
        });
  }

  public void start(String id, long uid) {
    resultTimer.stop();
    roundId = id;
    myId = uid;
    game = null;
    mine = null;
    bets = null;
    result = null;
    hidden = false;
    eye.setText("Ẩn bài");
    table.clearRound();
    Sound.play("deal");
    render();
  }

  public boolean accepts(String id) {
    return id != null && id.equals(roundId);
  }

  public void game(GameStateSnapshot value) {
    if (accepts(value.roundId()) && (game == null || value.stateVersion() > game.stateVersion())) {
      boolean turn = value.currentTurnUserId() != null && value.currentTurnUserId() == myId;
      boolean prior =
          game != null && game.currentTurnUserId() != null && game.currentTurnUserId() == myId;
      game = value;
      table.state(value);
      if (turn && !prior) Sound.play("your_turn");
      render();
    }
  }

  public void mine(PlayerStateSnapshot value) {
    if (accepts(value.roundId()) && (mine == null || value.stateVersion() >= mine.stateVersion())) {
      mine = value;
      table.mine(value);
      render();
    }
  }

  public void bets(BetStateSnapshot value) {
    if (accepts(value.roundId()) && (bets == null || value.stateVersion() >= bets.stateVersion())) {
      bets = value;
      table.bets(value);
      raiseTo.setText(
          value
              .currentHighBet()
              .add(room == null ? BigDecimal.TEN : room.betAmount())
              .stripTrailingZeros()
              .toPlainString());
      render();
    }
  }

  public void result(RoundResult value) {
    if (!accepts(value.roundId()) || result != null) return;
    result = value;
    resultDeadline =
        System.nanoTime()
            + java.util.concurrent.TimeUnit.SECONDS.toNanos(RoundResult.DISPLAY_SECONDS);
    resultTimer.restart();
    table.result(value);
    var me =
        value.playerResults().stream().filter(p -> p.userId() == myId).findFirst().orElse(null);
    if (me != null && !"CANCELLED".equals(value.roundStatus()))
      Sound.play(me.profit().signum() >= 0 ? "win" : "lose");
    render();
  }

  private void updateResultClock() {
    if (result == null) {
      resultTimer.stop();
      return;
    }
    if (System.nanoTime() >= resultDeadline) finishResult();
    render();
  }

  private void finishResult() {
    resultTimer.stop();
    roundId = null;
    game = null;
    mine = null;
    bets = null;
    result = null;
    hidden = false;
    eye.setText("Ẩn bài");
    table.clearRound();
  }

  public void setActionsEnabled(boolean value) {
    enabled = value;
    render();
  }

  public boolean active() {
    return game != null && result == null && "BETTING".equals(game.phase());
  }

  private void render() {
    boolean turn =
        enabled
            && active()
            && game.currentTurnUserId() != null
            && game.currentTurnUserId() == myId
            && mine != null
            && bets != null
            && !mine.folded()
            && !mine.allIn();
    var due =
        bets == null || mine == null
            ? BigDecimal.ZERO
            : bets.currentHighBet().subtract(mine.myCurrentBet()).max(BigDecimal.ZERO);
    call.setText(
        due.signum() == 0
            ? "Kiểm tra"
            : "Theo " + Theme.points(mine == null ? due : due.min(mine.myBalance())));
    call.setEnabled(turn);
    fold.setEnabled(turn);
    allIn.setEnabled(turn);
    raise.setEnabled(
        turn && mine.myBalance().add(mine.myCurrentBet()).compareTo(bets.currentHighBet()) > 0);
    raiseTo.setEnabled(raise.isEnabled());
    eye.setEnabled(mine != null);
    String text;
    if (result != null) {
      var me =
          result.playerResults().stream().filter(p -> p.userId() == myId).findFirst().orElse(null);
      text =
          "CANCELLED".equals(result.roundStatus())
              ? "Ván đã hủy. Điểm được hoàn lại."
              : me == null
                  ? "Ván đã kết thúc."
                  : me.profit().signum() > 0
                      ? "Bạn thắng +" + Theme.points(me.profit()) + " điểm!"
                      : me.profit().signum() < 0
                          ? "Ván này: " + Theme.points(me.profit()) + " điểm."
                          : "Hòa ván · Điểm không thay đổi.";
      long seconds =
          Math.max(0, (resultDeadline - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
      text += "  Kết quả tự ẩn sau " + seconds + " giây. Sau đó bạn quyết định có chơi tiếp.";
    } else if (active()) {
      if (mine == null) text = "Bạn đang xem ván chơi.";
      else if (mine.folded()) text = "Bạn đã úp bài. Chờ kết quả ván này.";
      else if (mine.allIn()) text = "Bạn đã tất tay. Chờ so bài.";
      else if (game.currentTurnUserId() != null && game.currentTurnUserId() == myId)
        text = "Đến lượt bạn · Chọn hành động trong 30 giây";
      else {
        String name =
            room == null
                ? "người chơi"
                : room.players().stream()
                    .filter(p -> java.util.Objects.equals(p.userId(), game.currentTurnUserId()))
                    .map(RoomSnapshot.PlayerSlot::displayName)
                    .findFirst()
                    .orElse("người chơi");
        text = "Chờ " + name + " quyết định…";
      }
    } else
      text =
          room != null && room.players().size() < 2
              ? "Mời thêm một người vào bàn để bắt đầu."
              : "Bấm Sẵn sàng. Chủ bàn bắt đầu khi mọi người đã sẵn sàng.";
    if (result == null && room != null && "RESULT".equals(room.status()))
      text = "Đang chuyển sang bàn chờ. Bạn sẽ chọn sẵn sàng cho ván tiếp theo.";
    controls.setVisible(roundId != null && result == null);
    boolean waiting =
        room != null && "WAITING".equals(room.status()) && result == null && roundId == null;
    waitingPanel.setVisible(waiting);
    if (waiting) {
      var me = room.players().stream().filter(p -> p.userId() == myId).findFirst().orElse(null);
      boolean spectator = me != null && "SPECTATOR".equals(me.status());
      waitingTitle.setText(
          spectator
              ? "BẠN ĐANG XEM"
              : me != null && me.ready() ? "BẠN ĐÃ SẴN SÀNG" : "SẴN SÀNG CHO VÁN MỚI");
      waitingTitle.setForeground(me != null && me.ready() ? Theme.GREEN : Theme.GOLD);
      waitingBet.setText("Cược cơ bản: " + Theme.points(room.betAmount()) + " điểm / người");
      var eligible = room.players().stream().filter(p -> "PLAYING".equals(p.status())).toList();
      long count = eligible.stream().filter(RoomSnapshot.PlayerSlot::ready).count();
      waitingCount.setText(
          spectator
              ? "Chưa đủ điểm để tham gia ván mới."
              : count
                  + " / "
                  + eligible.size()
                  + " người đã sẵn sàng · Cần ít nhất "
                  + room.minPlayers()
                  + " người");
    }
    status.setText(text);
    status.setForeground(turn ? Theme.GOLD : Theme.MUTED);
  }

  public void clear() {
    resultTimer.stop();
    roundId = null;
    game = null;
    mine = null;
    bets = null;
    result = null;
    room = null;
    hidden = false;
    enabled = false;
    table.clear();
    render();
  }
}
