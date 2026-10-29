package com.game3cay.gui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** A compact session header shared by the lobby and the game table. */
final class SessionHeader extends JPanel {
    SessionHeader(JLabel account, JLabel balance, JButton leaderboard, JButton history,
                  JButton points, JButton profile, JButton help, JButton sound, JButton logout) {
        super(new BorderLayout(24, 0));
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(14, 28, 14, 28)));
        var brand = Theme.transparent(new GridLayout(0, 1, 0, 0));
        var logo = Theme.label("♠  BA CÂY", 23, Theme.GOLD);
        logo.setFont(Theme.font(23, true));
        brand.add(logo);
        brand.add(Theme.label("ONLINE · CHƠI CÙNG BẠN BÈ", 9, Theme.MUTED));
        brand.setPreferredSize(new Dimension(160, 50));
        add(brand, BorderLayout.WEST);

        var navigation = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 5));
        leaderboard.setText("Xếp hạng");
        history.setText("Lịch sử ván");
        for (JButton button : new JButton[]{leaderboard, history, points}) {
            button.setPreferredSize(new Dimension(button == points ? 160 : 110, 40));
            button.setFont(Theme.font(13, true));
            navigation.add(button);
        }
        add(navigation, BorderLayout.CENTER);

        var identity = Theme.transparent(new GridLayout(0, 1, 0, 0));
        account.setFont(Theme.font(12, false));
        account.setForeground(Theme.MUTED);
        balance.setFont(Theme.font(20, true));
        identity.add(account);
        identity.add(balance);
        identity.setPreferredSize(new Dimension(184, 50));
        var actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(identity);
        profile.setPreferredSize(new Dimension(76, 40));
        actions.add(profile);
        var more = new Theme.Button("•••", "plain");
        more.setPreferredSize(new Dimension(44, 40));
        more.setToolTipText("Luật chơi, âm thanh và đăng xuất");
        more.getAccessibleContext().setAccessibleName("Tùy chọn tài khoản");
        var popup = new JPopupMenu();
        for (JButton source : new JButton[]{help, sound, logout}) {
            if (source == logout) popup.addSeparator();
            var item = new JMenuItem();
            item.setBorder(new EmptyBorder(10, 16, 10, 24));
            item.addActionListener(e -> source.doClick());
            popup.add(item);
            popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    item.setText(source.getText()); item.setEnabled(source.isEnabled());
                }
                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
            });
        }
        more.addActionListener(e -> popup.show(more, 0, more.getHeight() + 6));
        actions.add(more);
        add(actions, BorderLayout.EAST);
    }
}
