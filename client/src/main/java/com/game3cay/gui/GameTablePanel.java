package com.game3cay.gui;

import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.HandType;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoundResult;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;

public final class GameTablePanel extends JPanel {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int BASE_WIDTH = 1100;
    private static final int BASE_HEIGHT = 680;
    private static final long TURN_DURATION_NANOS = 30_000_000_000L;

    private RoomSnapshot room;
    private long myId;
    private GameStateSnapshot game;
    private PlayerStateSnapshot mine;
    private BetStateSnapshot bets;
    private RoundResult result;
    private boolean hidden;
    private JComponent waitingOverlay;
    private long dealtAt;
    private long turnAt;
    private int lastBeepSecond = -1;

    private final Timer animation = new Timer(25, e -> repaint());

    private final List<FlyingChip> flyingChips = new CopyOnWriteArrayList<>();

    private static final class FlyingChip {
        final double startX, startY;
        final double endX, endY;
        final double ctrlX, ctrlY;
        final long startNanos;
        final long durationNanos;

        FlyingChip(double startX, double startY, double endX, double endY, double ctrlX, double ctrlY, long startNanos, long durationNanos) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.ctrlX = ctrlX;
            this.ctrlY = ctrlY;
            this.startNanos = startNanos;
            this.durationNanos = durationNanos;
        }

        double progress(long nowNanos) {
            if (nowNanos < startNanos) return 0.0;
            return Math.min(1.0, (double) (nowNanos - startNanos) / durationNanos);
        }

        double currentX(double t) {
            return (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * ctrlX + t * t * endX;
        }

        double currentY(double t) {
            return (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * ctrlY + t * t * endY;
        }
    }

    public GameTablePanel() {
        setLayout(null);
        setOpaque(false);
        setPreferredSize(new Dimension(BASE_WIDTH, BASE_HEIGHT));
    }

    public void setWaitingOverlay(JComponent overlay) {
        if (waitingOverlay != null) {
            remove(waitingOverlay);
        }
        waitingOverlay = overlay;
        add(overlay);
        revalidate();
    }

    @Override
    public void doLayout() {
        if (waitingOverlay == null) {
            return;
        }
        double scale = Math.min((double) getWidth() / BASE_WIDTH, (double) getHeight() / BASE_HEIGHT);
        int ox = (int) ((getWidth() - BASE_WIDTH * scale) / 2);
        int oy = (int) ((getHeight() - BASE_HEIGHT * scale) / 2);
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
        this.room = r;
        this.myId = uid;
        repaint();
    }

    public void state(GameStateSnapshot g) {
        if (game == null
                || !Objects.equals(game.currentTurnUserId(), g.currentTurnUserId())
                || game.stateVersion() != g.stateVersion()) {
            turnAt = System.nanoTime();
            lastBeepSecond = -1;
        }
        this.game = g;
        repaint();
    }

    public void mine(PlayerStateSnapshot p) {
        this.mine = p;
        repaint();
    }

    public void bets(BetStateSnapshot b) {
        this.bets = b;
        repaint();
    }

    public void result(RoundResult r) {
        this.result = r;
        this.turnAt = 0;
        this.lastBeepSecond = -1;
        startPotFlyAnimation(r);
        repaint();
    }

    private void startPotFlyAnimation(RoundResult r) {
        if (room == null || r == null || "CANCELLED".equals(r.roundStatus())) {
            return;
        }
        var winners = r.playerResults().stream().filter(p -> "WIN".equals(p.result())).toList();
        if (winners.isEmpty()) {
            return;
        }

        int anchor = room.players().stream()
                .filter(p -> p.userId() == myId)
                .mapToInt(RoomSnapshot.PlayerSlot::seatNumber)
                .findFirst()
                .orElse(1);

        int winnerSeat = room.players().stream()
                .filter(p -> p.userId() == winners.getFirst().userId())
                .mapToInt(RoomSnapshot.PlayerSlot::seatNumber)
                .findFirst()
                .orElse(1);

        int slot = (winnerSeat - anchor + 8) % 8;
        double angle = Math.PI / 2 + 2 * Math.PI * slot / 8;
        int wx = (int) (550 + 445 * Math.cos(angle));
        int wy = (int) (365 + 242 * Math.sin(angle));

        flyingChips.clear();
        long now = System.nanoTime();
        for (int i = 0; i < 12; i++) {
            long delay = (long) (i * 35_000_000L);
            double ctrlX = 550 + (Math.random() - 0.5) * 180;
            double ctrlY = 370 - 70 - Math.random() * 90;
            flyingChips.add(new FlyingChip(550, 370, wx, wy, ctrlX, ctrlY, now + delay, 550_000_000L));
        }
        Sound.playPotWin();
    }

    public void hideCards(boolean value) {
        this.hidden = value;
        repaint();
    }

    public void clearRound() {
        this.game = null;
        this.mine = null;
        this.bets = null;
        this.result = null;
        this.hidden = false;
        this.dealtAt = System.nanoTime();
        this.lastBeepSecond = -1;
        this.flyingChips.clear();
        repaint();
    }

    public void clear() {
        this.room = null;
        clearRound();
    }

    @Override
    protected void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        var g = (Graphics2D) gg.create();
        Assets.quality(g);

        double scale = Math.min((double) getWidth() / BASE_WIDTH, (double) getHeight() / BASE_HEIGHT);
        g.translate((getWidth() - BASE_WIDTH * scale) / 2, (getHeight() - BASE_HEIGHT * scale) / 2);
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

        long now = System.nanoTime();
        boolean anyFlying = false;
        for (FlyingChip chip : flyingChips) {
            double t = chip.progress(now);
            if (t > 0 && t < 1.0) {
                anyFlying = true;
                double cx = chip.currentX(t);
                double cy = chip.currentY(t);
                g.drawImage(Assets.get("chips.png", 60), (int) cx - 18, (int) cy - 14, 36, 26, null);
            }
        }
        if (!anyFlying && !flyingChips.isEmpty()) {
            flyingChips.clear();
        }

        int anchor = room.players().stream()
                .filter(p -> p.userId() == myId)
                .mapToInt(RoomSnapshot.PlayerSlot::seatNumber)
                .findFirst()
                .orElse(1);

        for (int slot = 0; slot < 8; slot++) {
            int seat = (anchor - 1 + slot) % 8 + 1;
            var p = room.players().stream().filter(s -> s.seatNumber() == seat).findFirst().orElse(null);
            double angle = Math.PI / 2 + 2 * Math.PI * slot / 8;
            int x = (int) (550 + 445 * Math.cos(angle));
            int y = (int) (365 + 242 * Math.sin(angle));
            drawSeat(g, p, seat, x, y);
            if (p != null) {
                drawHand(g, p, angle);
            }
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
            g.setColor(new Color(8, 18, 34, 230));
            g.fillRoundRect(420, 339, 260, 76, 30, 30);
            g.setColor(new Color(212, 175, 55, 180));
            g.drawRoundRect(420, 339, 260, 76, 30, 30);
            g.drawImage(Assets.get("chips.png", 110), 436, 352, 65, 46, null);

            g.setFont(Theme.font(10, true));
            center(g, "TỔNG ĐIỂM TRÊN BÀN", 580, 360, Theme.MUTED);
            g.setFont(Theme.font(26, true));
            center(g, Theme.points(bets.mainPot()), 580, 391, Theme.GOLD);
        } else {
            g.setFont(Theme.font(14, false));
            center(g, Theme.points(room.betAmount()) + " điểm cơ bản  ·  3 lá mỗi người", 550, 352, new Color(230, 236, 245));
            g.setFont(Theme.font(12, false));
            center(g, "Sẵn sàng để bắt đầu", 550, 377, Theme.MUTED);
        }

        if (result != null) {
            String label;
            var winners = result.playerResults().stream().filter(p -> "WIN".equals(p.result())).toList();
            if ("CANCELLED".equals(result.roundStatus())) {
                label = "VÁN ĐÃ HỦY · HOÀN ĐIỂM";
            } else if (winners.isEmpty()) {
                label = "KẾT THÚC VÁN";
            } else {
                label = shorten(g, winners.getFirst().displayName(), 215) + " THẮNG";
            }
            g.setColor(new Color(10, 22, 40, 240));
            g.fillRoundRect(377, 289, 346, 40, 17, 17);
            g.setColor(Theme.GOLD);
            g.drawRoundRect(377, 289, 346, 40, 17, 17);
            g.setFont(Theme.font(17, true));
            center(g, label, 550, 315, Theme.GOLD);
        }
    }

    private void drawSeat(Graphics2D g, RoomSnapshot.PlayerSlot p, int seat, int x, int y) {
        int w = 174;
        int h = 68;
        boolean self = p != null && p.userId() == myId;
        boolean turn = p != null
                && result == null
                && game != null
                && game.currentTurnUserId() != null
                && game.currentTurnUserId() == p.userId();

        int left = x - w / 2;
        int top = y - h / 2;

        if (p == null) {
            g.setColor(new Color(8, 18, 32, 140));
            g.fillRoundRect(left, top, w, h, 19, 19);
            g.setColor(new Color(212, 175, 55, 80));
            g.drawRoundRect(left, top, w, h, 19, 19);
            g.setFont(Theme.font(12, false));
            center(g, "Ghế " + seat + " · Trống", x, y + 5, new Color(150, 172, 198, 160));
            return;
        }

        double elapsedNanos = System.nanoTime() - turnAt;
        double fraction = Math.max(0, 1.0 - elapsedNanos / TURN_DURATION_NANOS);
        double remainingSecs = Math.max(0, (TURN_DURATION_NANOS - elapsedNanos) / 1_000_000_000.0);
        boolean isUrgent = turn && remainingSecs <= 5.0 && remainingSecs > 0;

        if (turn) {
            if (isUrgent) {
                int alpha = (int) (110 + 90 * Math.sin(System.nanoTime() / 100_000_000.0));
                g.setColor(new Color(230, 50, 50, Math.clamp(alpha, 40, 220)));
                g.fillRoundRect(left - 7, top - 7, w + 14, h + 14, 26, 26);

                if (self) {
                    int secInt = (int) Math.ceil(remainingSecs);
                    if (secInt != lastBeepSecond && secInt > 0 && secInt <= 5) {
                        lastBeepSecond = secInt;
                        Sound.playUrgentBeep();
                    }
                }
            } else {
                g.setColor(new Color(235, 195, 110, 45));
                g.fillRoundRect(left - 5, top - 5, w + 10, h + 10, 24, 24);
                if (self) {
                    lastBeepSecond = -1;
                }
            }
        }

        g.setPaint(new GradientPaint(left, top, new Color(18, 36, 62), left, top + h, new Color(9, 18, 32)));
        g.fillRoundRect(left, top, w, h, 17, 17);
        g.setColor(isUrgent ? Theme.RED : (turn || self ? Theme.GOLD : new Color(140, 120, 75)));
        g.setStroke(new BasicStroke(turn ? (isUrgent ? 3.0f : 2.5f) : 1.2f));
        g.drawRoundRect(left, top, w, h, 17, 17);

        var avatar = Assets.get("avatars/avatar_player_" + ((p.userId() - 1) % 6 + 1) + ".png", 100);
        var clipped = (Graphics2D) g.create();
        clipped.setClip(new Ellipse2D.Double(left + 9, top + 10, 47, 47));
        clipped.drawImage(avatar, left + 8, top + 9, 49, 49, null);
        clipped.dispose();

        g.setColor(Theme.GOLD);
        g.drawOval(left + 9, top + 10, 47, 47);

        g.setFont(Theme.font(13, true));
        g.setColor(self ? Theme.GOLD : Theme.TEXT);
        g.drawString(shorten(g, p.displayName(), 99), left + 65, top + 25);

        var playerBet = (bets == null) ? null : bets.playerBets().stream()
                .filter(b -> b.userId() == p.userId())
                .findFirst()
                .orElse(null);

        var playerResult = (result == null) ? null : result.playerResults().stream()
                .filter(r -> r.userId() == p.userId())
                .findFirst()
                .orElse(null);

        String sub = "PLAYING".equals(room.status()) ? "Đang chơi" : p.ready() ? "✓ Sẵn sàng" : "Chờ sẵn sàng";
        Color color = p.ready() ? Theme.GREEN : Theme.MUTED;

        if ("SPECTATOR".equals(p.status())) {
            sub = "Đang xem";
        }

        if (playerBet != null) {
            sub = playerBet.folded()
                    ? "Đã úp bài"
                    : playerBet.allIn() ? "Tất tay" : Theme.points(playerBet.currentBet()) + " điểm";
            color = playerBet.folded() ? Theme.MUTED : Theme.GOLD;
        }

        if (playerResult != null) {
            sub = (playerResult.profit().signum() > 0 ? "+" : "") + Theme.points(playerResult.profit()) + " điểm";
            color = playerResult.profit().signum() >= 0 ? Theme.GREEN : Theme.RED;
        }

        if (result != null && "WAITING".equals(room.status()) && p.ready()) {
            sub = "✓ Sẵn sàng";
            color = Theme.GREEN;
        }

        g.setFont(Theme.font(12, false));
        g.setColor(color);
        g.drawString(sub, left + 65, top + 46);

        if (room.ownerUserId() == p.userId()) {
            g.drawImage(Assets.get("icons/crown.png", 30), left + w - 19, top - 11, 25, 25, null);
        }

        if (self) {
            g.setFont(Theme.font(10, true));
            center(g, "BẠN", left + 33, top + h + 13, Theme.GOLD);
        }

        if (turn) {
            g.setColor(new Color(10, 20, 35, 200));
            g.fillRoundRect(left + 14, top + h - 6, w - 28, 4, 3, 3);

            g.setColor(isUrgent ? Theme.RED : Theme.GOLD);
            g.fillRoundRect(left + 14, top + h - 6, (int) ((w - 28) * fraction), 4, 3, 3);
        }

        g.setStroke(new BasicStroke(1.0f));
    }

    private void drawHand(Graphics2D g, RoomSnapshot.PlayerSlot p, double angle) {
        boolean self = p.userId() == myId;
        var playerBet = (bets == null) ? null : bets.playerBets().stream()
                .filter(b -> b.userId() == p.userId())
                .findFirst()
                .orElse(null);

        var playerResult = (result == null) ? null : result.playerResults().stream()
                .filter(r -> r.userId() == p.userId())
                .findFirst()
                .orElse(null);

        boolean inRound = (playerBet != null) || (playerResult != null) || (self && mine != null);
        if (!inRound) {
            return;
        }

        List<Card> cards = (self && mine != null && !hidden)
                ? mine.myCards()
                : (playerResult != null && playerResult.hand() != null ? playerResult.hand().cards() : List.of());

        if (self && hidden) {
            cards = List.of();
        }

        int cw = self ? 91 : 48;
        int ch = self ? 127 : 67;
        int step = self ? 68 : 29;
        int cx = (int) (550 + 306 * Math.cos(angle));
        int cy = (int) (365 + 145 * Math.sin(angle));
        if (self) {
            cy -= 18;
        }
        int left = cx - (cw + 2 * step) / 2;
        int top = cy - ch / 2;

        boolean folded = playerBet != null && playerBet.folded();
        var hand = (Graphics2D) g.create();
        if (folded && !self) {
            hand.setComposite(AlphaComposite.SrcOver.derive(0.43f));
        }

        for (int i = 0; i < 3; i++) {
            int dy = 0;
            if (self && result == null) {
                dy = (int) (Math.max(0, 1.0 - (double) (System.nanoTime() - dealtAt) / 700_000_000.0) * 42.0 * (i + 1) / 3.0);
            }
            hand.setColor(new Color(0, 0, 0, 100));
            hand.fillRoundRect(left + i * step + 3, top + dy + 5, cw, ch, 6, 6);

            BufferedImage image = (cards.size() > i) ? Assets.card(cards.get(i)) : Assets.get("cards/back.png", 200);
            hand.drawImage(image, left + i * step, top + dy, cw, ch, null);
        }
        hand.dispose();

        if (playerResult != null && playerResult.hand() != null) {
            var h = playerResult.hand();
            String handScore = (h.handType() == HandType.DIEM) ? h.score() + " điểm" : h.handType().displayName();
            g.setFont(Theme.font(12, true));
            center(g, handScore, cx, cy > 410 ? top - 10 : top + ch + 17, Theme.GOLD);
        }
    }

    private static void center(Graphics2D g, String text, int x, int y, Color c) {
        g.setColor(c);
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y);
    }

    private static String shorten(Graphics2D g, String text, int width) {
        if (text == null) {
            return "Người chơi";
        }
        String s = text;
        while (s.length() > 1 && g.getFontMetrics().stringWidth(s) > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s.equals(text) ? s : s + "…";
    }
}
