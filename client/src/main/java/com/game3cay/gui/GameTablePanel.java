package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;

/** Table artwork with real seats and hands. Never derives an opponent's hidden cards. */
public final class GameTablePanel extends JPanel {
  private RoomSnapshot room;
  private long myId;
  private GameStateSnapshot game;
  private PlayerStateSnapshot mine;
  private BetStateSnapshot bets;
  private RoundResult result;
  private boolean hidden;
  private JComponent waitingOverlay;
  private long dealtAt, turnAt;
  private final Timer animation = new Timer(100, e -> repaint());

  public GameTablePanel() {
    setLayout(null);
    setOpaque(false);
    setPreferredSize(new Dimension(1100, 680));
  }

  public void setWaitingOverlay(JComponent overlay) {
    if (waitingOverlay != null) remove(waitingOverlay);
    waitingOverlay = overlay;
    add(overlay);
    revalidate();
  }

  @Override
  public void doLayout() {
    if (waitingOverlay == null) return;
    double scale = Math.min(getWidth() / 1100.0, getHeight() / 680.0);
    int ox = (int) ((getWidth() - 1100 * scale) / 2);
    int oy = (int) ((getHeight() - 680 * scale) / 2);
    waitingOverlay.setBounds(
        ox + (int) (300 * scale),
        oy + (int) (255 * scale),
        (int) (500 * scale),
        (int) (220 * scale));
  }

  @Override
  public void addNotify() {
    super.addNotify();
    animation.start();
  }

  @Override
  public void removeNotify() {
    animation.stop();
    super.removeNotify();
  }

  public void room(RoomSnapshot r, long uid) {
    room = r;
    myId = uid;
    repaint();
  }

  public void state(GameStateSnapshot g) {
    if (game == null
        || !java.util.Objects.equals(game.currentTurnUserId(), g.currentTurnUserId())
        || game.stateVersion() != g.stateVersion()) turnAt = System.nanoTime();
    game = g;
    repaint();
  }

  public void mine(PlayerStateSnapshot p) {
    mine = p;
    repaint();
  }

  public void bets(BetStateSnapshot b) {
    bets = b;
    repaint();
  }

  public void result(RoundResult r) {
    result = r;
    repaint();
  }

  public void hideCards(boolean value) {
    hidden = value;
    repaint();
  }

  public void clearRound() {
    game = null;
    mine = null;
    bets = null;
    result = null;
    hidden = false;
    dealtAt = System.nanoTime();
    repaint();
  }

  public void clear() {
    room = null;
    clearRound();
  }

  @Override
  protected void paintComponent(Graphics gg) {
    super.paintComponent(gg);
    var g = (Graphics2D) gg.create();
    Assets.quality(g);
    double scale = Math.min(getWidth() / 1100.0, getHeight() / 680.0);
    g.translate((getWidth() - 1100 * scale) / 2, (getHeight() - 680 * scale) / 2);
    g.scale(scale, scale);
    g.setColor(new Color(0, 0, 0, 55));
    g.fillOval(98, 154, 910, 460);
    g.drawImage(Assets.get("dealer.png", 220), 472, 2, 156, 230, null);
    g.drawImage(Assets.get("table.png", 1100), 95, 132, 910, 475, null);
    if (room == null) {
      g.dispose();
      return;
    }
    drawCenter(g);
    int anchor =
        room.players().stream()
            .filter(p -> p.userId() == myId)
            .mapToInt(RoomSnapshot.PlayerSlot::seatNumber)
            .findFirst()
            .orElse(1);
    for (int slot = 0; slot < 8; slot++) {
      int seat = (anchor - 1 + slot) % 8 + 1;
      var p = room.players().stream().filter(s -> s.seatNumber() == seat).findFirst().orElse(null);
      double angle = Math.PI / 2 + 2 * Math.PI * slot / 8;
      int x = (int) (550 + 445 * Math.cos(angle)), y = (int) (365 + 242 * Math.sin(angle));
      drawSeat(g, p, seat, x, y);
      if (p != null) drawHand(g, p, angle);
    }
    g.dispose();
  }

  private void drawCenter(Graphics2D g) {
    if (bets == null && result == null) {
      g.setFont(Theme.font(30, true));
      center(g, "BA CÂY", 550, 234, new Color(214, 197, 138, 180));
      if (!"WAITING".equals(room.status())) {
        g.setFont(Theme.font(15, true));
        center(g, "Đang chuẩn bị bàn cho ván mới…", 550, 365, Theme.GOLD);
      }
      return;
    }
    if (result == null) {
      g.setFont(Theme.font(31, true));
      center(g, "BA CÂY", 550, 319, new Color(214, 197, 138, 135));
    }
    if (bets != null) {
      g.setColor(new Color(0, 20, 16, 210));
      g.fillRoundRect(420, 339, 260, 76, 30, 30);
      g.setColor(new Color(181, 159, 91));
      g.drawRoundRect(420, 339, 260, 76, 30, 30);
      g.drawImage(Assets.get("chips.png", 110), 436, 352, 65, 46, null);
      g.setFont(Theme.font(10, true));
      center(g, "TỔNG ĐIỂM TRÊN BÀN", 580, 360, Theme.MUTED);
      g.setFont(Theme.font(26, true));
      center(g, Theme.points(bets.mainPot()), 580, 391, Theme.GOLD);
    } else {
      g.setFont(Theme.font(14, false));
      center(
          g,
          Theme.points(room.betAmount()) + " điểm cơ bản  ·  3 lá mỗi người",
          550,
          352,
          new Color(208, 216, 189));
      g.setFont(Theme.font(12, false));
      center(g, "Sẵn sàng để bắt đầu", 550, 377, Theme.MUTED);
    }
    if (result != null) {
      String label;
      var winners = result.playerResults().stream().filter(p -> "WIN".equals(p.result())).toList();
      if ("CANCELLED".equals(result.roundStatus())) label = "VÁN ĐÃ HỦY · HOÀN ĐIỂM";
      else if (winners.isEmpty()) label = "KẾT THÚC VÁN";
      else label = shorten(g, winners.getFirst().displayName(), 215) + " THẮNG";
      g.setColor(new Color(13, 29, 22, 240));
      g.fillRoundRect(377, 289, 346, 40, 17, 17);
      g.setColor(Theme.GOLD);
      g.drawRoundRect(377, 289, 346, 40, 17, 17);
      g.setFont(Theme.font(17, true));
      center(g, label, 550, 315, Theme.GOLD);
    }
  }

  private void drawSeat(Graphics2D g, RoomSnapshot.PlayerSlot p, int seat, int x, int y) {
    int w = 174, h = 68;
    boolean self = p != null && p.userId() == myId;
    boolean turn =
        p != null
            && result == null
            && game != null
            && game.currentTurnUserId() != null
            && game.currentTurnUserId() == p.userId();
    int left = x - w / 2, top = y - h / 2;
    if (p == null) {
      g.setColor(new Color(5, 22, 19, 125));
      g.fillRoundRect(left, top, w, h, 19, 19);
      g.setColor(new Color(183, 166, 115, 70));
      g.drawRoundRect(left, top, w, h, 19, 19);
      g.setFont(Theme.font(12, false));
      center(g, "Ghế " + seat + " · Trống", x, y + 5, new Color(155, 174, 150, 150));
      return;
    }
    if (turn) {
      g.setColor(new Color(235, 195, 110, 45));
      g.fillRoundRect(left - 5, top - 5, w + 10, h + 10, 24, 24);
    }
    g.setPaint(
        new GradientPaint(left, top, new Color(35, 48, 35), left, top + h, new Color(7, 22, 19)));
    g.fillRoundRect(left, top, w, h, 17, 17);
    g.setColor(turn || self ? Theme.GOLD : new Color(125, 112, 73));
    g.setStroke(new BasicStroke(turn ? 2.5f : 1.2f));
    g.drawRoundRect(left, top, w, h, 17, 17);
    var av = Assets.get("avatars/avatar_player_" + ((p.userId() - 1) % 6 + 1) + ".png", 100);
    var a = (Graphics2D) g.create();
    a.setClip(new Ellipse2D.Double(left + 9, top + 10, 47, 47));
    a.drawImage(av, left + 8, top + 9, 49, 49, null);
    a.dispose();
    g.setColor(Theme.GOLD);
    g.drawOval(left + 9, top + 10, 47, 47);
    g.setFont(Theme.font(13, true));
    g.setColor(self ? Theme.GOLD : Theme.TEXT);
    g.drawString(shorten(g, p.displayName(), 99), left + 65, top + 25);
    var pb =
        bets == null
            ? null
            : bets.playerBets().stream()
                .filter(b -> b.userId() == p.userId())
                .findFirst()
                .orElse(null);
    var pr =
        result == null
            ? null
            : result.playerResults().stream()
                .filter(r -> r.userId() == p.userId())
                .findFirst()
                .orElse(null);
    String sub =
        "PLAYING".equals(room.status()) ? "Đang chơi" : p.ready() ? "✓ Sẵn sàng" : "Chờ sẵn sàng";
    Color color = p.ready() ? Theme.GREEN : Theme.MUTED;
    if ("SPECTATOR".equals(p.status())) sub = "Đang xem";
    if (pb != null) {
      sub =
          pb.folded()
              ? "Đã úp bài"
              : pb.allIn() ? "Tất tay" : Theme.points(pb.currentBet()) + " điểm";
      color = pb.folded() ? Theme.MUTED : Theme.GOLD;
    }
    if (pr != null) {
      sub = (pr.profit().signum() > 0 ? "+" : "") + Theme.points(pr.profit()) + " điểm";
      color = pr.profit().signum() >= 0 ? Theme.GREEN : Theme.RED;
    }
    if (result != null && "WAITING".equals(room.status()) && p.ready()) {
      sub = "✓ Sẵn sàng";
      color = Theme.GREEN;
    }
    g.setFont(Theme.font(12, false));
    g.setColor(color);
    g.drawString(sub, left + 65, top + 46);
    if (room.ownerUserId() == p.userId())
      g.drawImage(Assets.get("icons/crown.png", 30), left + w - 19, top - 11, 25, 25, null);
    if (self) {
      g.setFont(Theme.font(10, true));
      center(g, "BẠN", left + 33, top + h + 13, Theme.GOLD);
    }
    if (turn) {
      double fraction = Math.max(0, 1 - (System.nanoTime() - turnAt) / 30_000_000_000.0);
      g.setColor(Theme.GOLD);
      g.fillRoundRect(left + 14, top + h - 5, (int) ((w - 28) * fraction), 3, 3, 3);
    }
    g.setStroke(new BasicStroke(1));
  }

  private void drawHand(Graphics2D g, RoomSnapshot.PlayerSlot p, double angle) {
    boolean self = p.userId() == myId;
    var pb =
        bets == null
            ? null
            : bets.playerBets().stream()
                .filter(b -> b.userId() == p.userId())
                .findFirst()
                .orElse(null);
    var pr =
        result == null
            ? null
            : result.playerResults().stream()
                .filter(r -> r.userId() == p.userId())
                .findFirst()
                .orElse(null);
    boolean inRound = pb != null || pr != null || (self && mine != null);
    if (!inRound) return;
    List<Card> cards =
        self && mine != null && !hidden
            ? mine.myCards()
            : pr != null && pr.hand() != null ? pr.hand().cards() : List.of();
    if (self && hidden) cards = List.of();
    int cw = self ? 91 : 48, ch = self ? 127 : 67, step = self ? 68 : 29;
    int cx = (int) (550 + 306 * Math.cos(angle)), cy = (int) (365 + 145 * Math.sin(angle));
    int left = cx - (cw + 2 * step) / 2, top = cy - ch / 2;
    boolean folded = pb != null && pb.folded();
    var hand = (Graphics2D) g.create();
    if (folded && !self) hand.setComposite(AlphaComposite.SrcOver.derive(.43f));
    for (int i = 0; i < 3; i++) {
      int dy = 0;
      if (self && result == null)
        dy =
            (int)
                (Math.max(0, 1 - (System.nanoTime() - dealtAt) / 700_000_000.0) * 42 * (i + 1) / 3);
      hand.setColor(new Color(0, 0, 0, 100));
      hand.fillRoundRect(left + i * step + 3, top + dy + 5, cw, ch, 6, 6);
      BufferedImage image =
          cards.size() > i ? Assets.card(cards.get(i)) : Assets.get("cards/back.png", 200);
      hand.drawImage(image, left + i * step, top + dy, cw, ch, null);
    }
    hand.dispose();
    if (pr != null && pr.hand() != null) {
      var h = pr.hand();
      String s = h.handType() == HandType.DIEM ? h.score() + " điểm" : h.handType().displayName();
      g.setFont(Theme.font(12, true));
      center(g, s, cx, cy > 410 ? top - 10 : top + ch + 17, Theme.GOLD);
    }
  }

  private static void center(Graphics2D g, String text, int x, int y, Color c) {
    g.setColor(c);
    g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y);
  }

  private static String shorten(Graphics2D g, String text, int width) {
    if (text == null) return "Người chơi";
    String s = text;
    while (s.length() > 1 && g.getFontMetrics().stringWidth(s) > width)
      s = s.substring(0, s.length() - 1);
    return s.equals(text) ? s : s + "…";
  }
}
