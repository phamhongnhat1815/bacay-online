package com.game3cay.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;

/**
 * Menu biểu cảm / Sticker tương tác nhanh trên bàn chơi.
 */
public final class EmotePopup extends JPopupMenu {

    private final Consumer<Emote> onSelect;

    public EmotePopup(Consumer<Emote> onSelect) {
        this.onSelect = onSelect;
        setBackground(new Color(12, 24, 42));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.GOLD, 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        var grid = new JPanel(new GridLayout(2, 4, 6, 6));
        grid.setOpaque(false);

        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 24);

        for (Emote emote : Emote.values()) {
            JButton btn = new JButton(emote.emoji());
            btn.setFont(emojiFont);
            btn.setToolTipText(emote.description());
            btn.setPreferredSize(new Dimension(46, 46));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(true);
            btn.setBackground(new Color(20, 36, 60));
            btn.setForeground(Color.WHITE);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            btn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    btn.setBackground(new Color(36, 62, 98));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    btn.setBackground(new Color(20, 36, 60));
                }
            });

            btn.addActionListener(e -> {
                setVisible(false);
                if (this.onSelect != null) {
                    this.onSelect.accept(emote);
                }
            });

            grid.add(btn);
        }

        add(grid);
    }

    public void showAt(Component invoker, int x, int y) {
        show(invoker, x, y);
    }
}
