package com.game3cay.gui;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.HandType;
import com.game3cay.shared.model.Rank;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/**
 * Hộp thoại tương tác "Nặn bài" (Card Squeeze Mode) cho phép người chơi
 * kéo vuốt lá bài thứ 3 để hé mở điểm số từng chút một.
 */
public final class CardSqueezeDialog extends JDialog {

    private final List<Card> cards;
    private final Runnable onFinished;
    private final SqueezeCanvas canvas;
    private boolean isRevealed = false;

    public CardSqueezeDialog(Frame owner, List<Card> cards, Runnable onFinished) {
        super(owner, "Nặn Bài Ba Cây", true);
        this.cards = (cards == null || cards.size() < 3) ? List.of() : List.copyOf(cards);
        this.onFinished = onFinished;

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        var root = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Glass-morphism navy gradient
                g2.setPaint(new GradientPaint(0, 0, new Color(14, 28, 50, 250), 0, getHeight(), new Color(8, 16, 30, 252)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(Theme.GOLD);
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 24, 24);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // Header
        var header = Theme.transparent(new BorderLayout());
        var title = Theme.label("🖐 NẶN BÀI BA CÂY", 18, Theme.GOLD);
        title.setFont(Theme.font(18, true));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        var subtitle = Theme.label("Dùng chuột kéo vuốt lá bài thứ 3 để hé mở chất và điểm số!", 12, Theme.MUTED);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);

        var titleBox = Theme.transparent(new BorderLayout(0, 4));
        titleBox.add(title, BorderLayout.NORTH);
        titleBox.add(subtitle, BorderLayout.SOUTH);
        header.add(titleBox, BorderLayout.CENTER);

        var closeX = new JButton("✕");
        closeX.setFont(Theme.font(14, true));
        closeX.setForeground(Theme.MUTED);
        closeX.setContentAreaFilled(false);
        closeX.setBorderPainted(false);
        closeX.setFocusPainted(false);
        closeX.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeX.addActionListener(e -> finishAndClose());
        header.add(closeX, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Center: Interactive Squeeze Area
        canvas = new SqueezeCanvas();
        root.add(canvas, BorderLayout.CENTER);

        // Footer buttons
        var footer = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 14, 0));
        var btnReveal = new Theme.Button("👁 Mở Hết Bài", "gold");
        btnReveal.setPreferredSize(new Dimension(140, 38));
        btnReveal.addActionListener(e -> canvas.revealAll());

        var btnDone = new Theme.Button("Xong", "navy");
        btnDone.setPreferredSize(new Dimension(100, 38));
        btnDone.addActionListener(e -> finishAndClose());

        footer.add(btnReveal);
        footer.add(btnDone);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    finishAndClose();
                }
            }
        });
    }

    private void finishAndClose() {
        dispose();
        if (onFinished != null) {
            onFinished.run();
        }
    }

    private final class SqueezeCanvas extends JPanel {
        private static final int CARD_W = 130;
        private static final int CARD_H = 182;
        private static final int C3_X = 350;
        private static final int C3_Y = 30;

        private int dragX = 0;
        private int dragY = 0;
        private int pressX = 0;
        private int pressY = 0;
        private boolean dragging = false;

        SqueezeCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(525, 275));

            var mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isRevealed) return;
                    if (e.getX() >= C3_X - 20 && e.getX() <= C3_X + CARD_W + 20
                            && e.getY() >= C3_Y - 20 && e.getY() <= C3_Y + CARD_H + 20) {
                        dragging = true;
                        pressX = e.getX();
                        pressY = e.getY();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!dragging) return;
                    dragging = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

                    double distance = Math.hypot(dragX, dragY);
                    if (distance > 70) {
                        revealAll();
                    } else {
                        dragX = 0;
                        dragY = 0;
                        repaint();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!dragging || isRevealed) return;
                    dragX = Math.clamp(e.getX() - pressX, -110, 110);
                    dragY = Math.clamp(e.getY() - pressY, -110, 110);
                    Sound.playCardFlip();
                    repaint();
                }
            };

            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        void revealAll() {
            if (!isRevealed) {
                isRevealed = true;
                Sound.playCardFlip();
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (cards.size() < 3) {
                g2.dispose();
                return;
            }

            int y = C3_Y;

            // Lá 1 (Mở sẵn)
            drawCard(g2, cards.get(0), 40, y);
            // Lá 2 (Mở sẵn)
            drawCard(g2, cards.get(1), 195, y);

            // Lá 3 (Nặn bài)
            // Lớp dưới: Lá bài thật
            drawCard(g2, cards.get(2), C3_X, y);

            // Lớp trên: Mặt sau lá bài (bị kéo lệch)
            if (!isRevealed) {
                int backX = C3_X + dragX;
                int backY = y + dragY;

                // Bóng đổ dưới lá bài bị kéo
                g2.setColor(new Color(0, 0, 0, 120));
                g2.fillRoundRect(backX + 6, backY + 6, CARD_W, CARD_H, 12, 12);

                BufferedImage backImg = Assets.get("cards/back.png", 200);
                g2.drawImage(backImg, backX, backY, CARD_W, CARD_H, null);

                // Viền sáng vàng khi đang kéo
                g2.setColor(dragging ? Theme.GOLD : new Color(212, 175, 55, 120));
                g2.setStroke(new BasicStroke(dragging ? 2.5f : 1.5f));
                g2.drawRoundRect(backX, backY, CARD_W, CARD_H, 12, 12);

                // Nhãn hướng dẫn dưới lá 3
                g2.setFont(Theme.font(11, false));
                g2.setColor(Theme.GOLD);
                String hint = dragging ? "Kéo tiếp để mở hết!" : "↕ Kéo lá bài để hé mở";
                int hintW = g2.getFontMetrics().stringWidth(hint);
                g2.drawString(hint, C3_X + (CARD_W - hintW) / 2, y + CARD_H + 20);
            }

            // Khi đã mở hết: Hiện huy hiệu điểm số rực rỡ!
            if (isRevealed) {
                String scoreTitle = evaluateHandTitle(cards);
                g2.setColor(new Color(10, 22, 42, 220));
                g2.fillRoundRect(80, y + CARD_H + 10, 365, 36, 18, 18);
                g2.setColor(Theme.GOLD);
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawRoundRect(80, y + CARD_H + 10, 365, 36, 18, 18);

                g2.setFont(Theme.font(16, true));
                int titleW = g2.getFontMetrics().stringWidth(scoreTitle);
                g2.drawString(scoreTitle, 80 + (365 - titleW) / 2, y + CARD_H + 34);
            }

            g2.dispose();
        }

        private void drawCard(Graphics2D g, Card c, int x, int y) {
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRoundRect(x + 4, y + 4, CARD_W, CARD_H, 12, 12);
            BufferedImage img = Assets.card(c);
            g.drawImage(img, x, y, CARD_W, CARD_H, null);
            g.setColor(new Color(255, 255, 255, 40));
            g.drawRoundRect(x, y, CARD_W, CARD_H, 12, 12);
        }
    }

    public static String evaluateHandTitle(List<Card> cards) {
        if (cards == null || cards.size() != 3) {
            return "";
        }
        Card c1 = cards.get(0);
        Card c2 = cards.get(1);
        Card c3 = cards.get(2);

        // 1. Sáp (3 lá cùng rank)
        if (c1.rank() == c2.rank() && c2.rank() == c3.rank()) {
            return "★ SÁP " + c1.rank().displayName() + " ★";
        }

        // 2. Bồ Đội (J, Q, K)
        if (c1.rank().isFaceCard() && c2.rank().isFaceCard() && c3.rank().isFaceCard()) {
            return "★ BỒ ĐỘI (3 TÂY) ★";
        }

        // 3. Liêng (3 lá liên tiếp)
        int[] sorted = {c1.rank().rankValue(), c2.rank().rankValue(), c3.rank().rankValue()};
        Arrays.sort(sorted);
        if (sorted[0] + 1 == sorted[1] && sorted[1] + 1 == sorted[2]) {
            return "★ LIÊNG " + c1.rank().displayName() + "-" + c2.rank().displayName() + "-" + c3.rank().displayName() + " ★";
        }
        // Liêng A-2-3 (2, 3, 14)
        if (sorted[0] == 2 && sorted[1] == 3 && sorted[2] == 14) {
            return "★ LIÊNG A-2-3 ★";
        }

        // 4. Điểm
        int sum = c1.pointValue() + c2.pointValue() + c3.pointValue();
        int score = sum % 10;
        if (score == 0) {
            return "★ 10 ĐIỂM (10 NƯỚC) ★";
        }
        return "★ " + score + " ĐIỂM ★";
    }
}
