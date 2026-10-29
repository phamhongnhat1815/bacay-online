package com.game3cay.gui;

import com.game3cay.shared.model.Card;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** The gesture UI is independent of the window, so closing never reveals a card. */
final class CardSqueezePanel extends JPanel {
    private final List<Card> cards;
    private final HandRevealState reveals;
    private final Runnable onChanged;
    private final long generation;
    private final JLabel progress = Theme.label("", 16, Theme.GOLD);
    private final JLabel turn = Theme.label("", 13, Theme.MUTED);
    private final Theme.Button openSelected = new Theme.Button("Mở lá đang chọn", "plain");
    private final Theme.Button openAll = new Theme.Button("Mở cả 3 lá", "plain");
    private final Canvas canvas = new Canvas();
    private int selected;

    CardSqueezePanel(List<Card> cards, HandRevealState reveals, Runnable onChanged, Runnable onClose) {
        super(new BorderLayout(0, 14));
        if (cards == null || cards.size() != 3) throw new IllegalArgumentException("A hand must contain three cards");
        this.cards = List.copyOf(cards);
        this.reveals = reveals;
        this.generation = reveals.generation();
        this.onChanged = onChanged;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));
        selected = nextUnopened(0);

        var heading = Theme.transparent(new BorderLayout(0, 10));
        var titleRow = Theme.transparent(new BorderLayout(12, 0));
        var title = Theme.label("Nặn bài của bạn", 24, Theme.TEXT);
        title.setFont(Theme.font(24, true));
        titleRow.add(title, BorderLayout.WEST); titleRow.add(progress, BorderLayout.EAST);
        heading.add(titleRow, BorderLayout.NORTH);
        var instructions = Theme.transparent(new GridLayout(0, 1, 0, 6));
        instructions.add(Theme.label("Giữ phần dưới lá bài và kéo lên từ từ; kéo đủ xa rồi thả để mở.", 13, Theme.MUTED));
        instructions.add(turn);
        heading.add(instructions, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        var bottom = Theme.transparent(new BorderLayout(0, 12));
        bottom.add(Theme.label("Phím ← / → chọn lá · Space mở lá đang chọn · Esc trở về bàn", 12, Theme.MUTED), BorderLayout.NORTH);
        var buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        var close = new Theme.Button("Trở về bàn", "gold");
        for (var button : new Theme.Button[]{openSelected, openAll, close}) {
            button.setPreferredSize(new Dimension(button == openSelected ? 156 : 132, 40));
            buttons.add(button);
        }
        openSelected.addActionListener(e -> openCard(selected));
        openAll.addActionListener(e -> {
            if (reveals.generation() != generation) return;
            reveals.revealAll(); canvas.resetPeek(); Sound.playCardFlip(); refresh(); onChanged.run();
        });
        close.addActionListener(e -> onClose.run());
        bottom.add(buttons, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() { public void actionPerformed(ActionEvent e) { onClose.run(); } });
        context("Thời gian cược vẫn chạy · Có thể trở về bàn bất cứ lúc nào", false);
        refresh();
    }

    void context(String text, boolean urgent) { turn.setText(text); turn.setForeground(urgent ? Theme.GOLD : Theme.MUTED); }
    Rectangle cardBounds(int index) { return canvas.cardBounds(index); }

    private int nextUnopened(int from) {
        for (int n = 0; n < 3; n++) { int i = (from + n) % 3; if (!reveals.revealed(i)) return i; }
        return from;
    }

    private void openCard(int index) {
        if (reveals.generation() != generation || reveals.revealed(index)) return;
        reveals.reveal(index); canvas.resetPeek();
        selected = nextUnopened((index + 1) % 3);
        Sound.playCardFlip(); refresh(); onChanged.run();
    }

    private void refresh() {
        progress.setText(reveals.complete() ? CardSqueezeDialog.evaluateHandTitle(cards) : "Đã mở " + reveals.count() + " / 3 lá");
        openSelected.setEnabled(!reveals.revealed(selected));
        openAll.setEnabled(!reveals.complete());
        canvas.repaint();
    }

    private final class Canvas extends JPanel {
        private int dragging = -1;
        private int pressY;
        private double peek;
        private double returningFrom;
        private long returnStarted;
        private final Timer returnTimer = new Timer(16, e -> {
            double fraction = Math.min(1, (System.nanoTime() - returnStarted) / 180_000_000.0);
            peek = returningFrom * (1 - fraction) * (1 - fraction);
            if (fraction >= 1) ((Timer) e.getSource()).stop();
            repaint();
        });

        Canvas() {
            setName("card-squeeze-canvas");
            setOpaque(false); setFocusable(true);
            setPreferredSize(new Dimension(600, 280));
            getAccessibleContext().setAccessibleName("Ba lá bài; dùng phím mũi tên chọn lá và Space để mở");
            var mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    for (int i = 0; i < 3; i++) if (cardBounds(i).contains(e.getPoint())) {
                        resetPeek(); selected = i; refresh(); requestFocusInWindow();
                        if (!reveals.revealed(i)) { dragging = i; pressY = e.getY(); }
                        break;
                    }
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragging < 0) return;
                    peek = Math.clamp((double) (pressY - e.getY()) / cardBounds(dragging).height, 0, 1);
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    int index = dragging; dragging = -1;
                    if (index < 0) return;
                    if (peek >= .60) openCard(index);
                    else {
                        returningFrom = peek; returnStarted = System.nanoTime(); returnTimer.restart();
                    }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    boolean canDrag = false;
                    for (int i = 0; i < 3; i++) if (cardBounds(i).contains(e.getPoint()) && !reveals.revealed(i)) canDrag = true;
                    setCursor(Cursor.getPredefinedCursor(canDrag ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
            bind(KeyEvent.VK_LEFT, "left", () -> select(-1));
            bind(KeyEvent.VK_RIGHT, "right", () -> select(1));
            bind(KeyEvent.VK_SPACE, "open", () -> openCard(selected));
            bind(KeyEvent.VK_ENTER, "openEnter", () -> openCard(selected));
        }

        private void bind(int key, String name, Runnable action) {
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
            getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent e) { action.run(); } });
        }
        private void select(int direction) { resetPeek(); selected = (selected + direction + 3) % 3; refresh(); }
        private void resetPeek() { returnTimer.stop(); dragging = -1; peek = 0; repaint(); }
        @Override public void removeNotify() { returnTimer.stop(); super.removeNotify(); }

        Rectangle cardBounds(int index) {
            int gap = 22;
            int width = Math.max(70, Math.min(156, Math.min((getWidth() - 2 * gap - 20) / 3, (getHeight() - 42) * 5 / 7)));
            int height = width * 7 / 5;
            int x = (getWidth() - (3 * width + 2 * gap)) / 2 + index * (width + gap);
            return new Rectangle(x, 8, width, height);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            Assets.quality(g);
            try {
                for (int i = 0; i < 3; i++) {
                    Rectangle r = cardBounds(i);
                    if (reveals.revealed(i)) Assets.drawCard(g, cards.get(i), r.x, r.y, r.width, r.height);
                    else {
                        Assets.drawCardBack(g, r.x, r.y, r.width, r.height);
                        if (i == selected && peek > 0) {
                            int exposed = (int) (r.height * peek);
                            int foldY = r.y + r.height - exposed;
                            // The cover moves up with the mouse; the face is revealed from its lower edge.
                            Graphics2D cover = (Graphics2D) g.create();
                            try {
                                cover.clip(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
                                cover.clipRect(r.x, r.y, r.width, r.height - exposed);
                                Assets.drawCardBack(cover, r.x, r.y - exposed, r.width, r.height);
                            } finally { cover.dispose(); }
                            Graphics2D face = (Graphics2D) g.create();
                            try {
                                face.clip(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
                                face.clipRect(r.x, foldY, r.width, exposed);
                                Assets.drawCard(face, cards.get(i), r.x, r.y, r.width, r.height);
                            } finally { face.dispose(); }
                            int curl = Math.min(12, Math.max(2, exposed / 5));
                            Graphics2D edge = (Graphics2D) g.create();
                            try {
                                edge.clip(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
                                edge.setPaint(new GradientPaint(0, foldY - curl, new Color(80, 93, 110),
                                        0, foldY, new Color(255, 245, 219)));
                                edge.fillRoundRect(r.x + 1, foldY - curl, r.width - 2, curl + 2, 8, 6);
                                edge.setColor(new Color(0, 0, 0, 60));
                                edge.fillRect(r.x + 2, foldY + 2, r.width - 4, 2);
                            } finally { edge.dispose(); }
                            g.setStroke(new BasicStroke(1.15f));
                            g.setColor(new Color(69, 83, 103));
                            g.drawRoundRect(r.x, r.y, r.width, r.height, 12, 12);
                        }
                    }
                    if (i == selected && !reveals.complete()) {
                        g.setColor(Theme.GOLD); g.setStroke(new BasicStroke(1.5f));
                        g.drawRoundRect(r.x - 3, r.y - 3, r.width + 6, r.height + 6, 14, 14);
                    }
                    g.setFont(Theme.font(12, false));
                    g.setColor(reveals.revealed(i) ? Theme.GREEN : Theme.MUTED);
                    String label = "Lá " + (i + 1) + (reveals.revealed(i) ? " · Đã mở" : i == selected ? " · ↑ Kéo lên" : " · Đang úp");
                    g.drawString(label, r.x + (r.width - g.getFontMetrics().stringWidth(label)) / 2, r.y + r.height + 25);
                }
            } finally { g.dispose(); }
        }
    }
}
