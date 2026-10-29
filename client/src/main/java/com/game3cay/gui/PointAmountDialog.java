package com.game3cay.gui;

import com.game3cay.shared.model.RoomSnapshot;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.swing.*;

public final class PointAmountDialog {
    public record Entry(long recipientId, BigDecimal amount, String message) {}
    private record Peer(long id, String name) {
        @Override public String toString() { return name + " (#" + id + ")"; }
    }
    private PointAmountDialog() {}

    public static Optional<Entry> show(JFrame owner, RoomSnapshot room, long userId, BigDecimal balance, boolean giving) {
        if (room == null || !"WAITING".equals(room.status())) return Optional.empty();
        List<Peer> peers = room.players().stream().filter(p -> p.userId() != userId)
                .map(p -> new Peer(p.userId(), p.displayName())).toList();
        if (peers.isEmpty()) {
            owner.getToolkit().beep();
            return Optional.empty();
        }
        JComboBox<Peer> recipient = new JComboBox<>(peers.toArray(Peer[]::new));
        recipient.setRenderer(new DefaultListCellRenderer() {
            { putClientProperty("html.disable", Boolean.TRUE); }
        });
        JTextField amount = new JTextField("100");
        JTextField message = new Theme.HintField("Lời nhắn cho người cùng bàn (tùy chọn)");
        Theme.field(amount);
        Theme.field(message);
        JPanel panel = Theme.transparent(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(Theme.label("Số dư của bạn: " + Theme.points(balance) + " điểm", 16, Theme.GOLD));
        panel.add(Box.createVerticalStrut(18));
        panel.add(row(giving ? "Người nhận" : "Bạn muốn xin ai?", recipient));
        panel.add(Box.createVerticalStrut(12));
        panel.add(row("Số điểm · Dùng dấu chấm cho phần thập phân", amount));
        panel.add(Box.createVerticalStrut(8));
        JPanel presets = Theme.transparent(new GridLayout(1, 3, 8, 0));
        for (String value : new String[]{"50", "100", "500"}) {
            Theme.Button preset = new Theme.Button(value, "plain");
            preset.addActionListener(e -> amount.setText(value));
            presets.add(preset);
        }
        presets.setPreferredSize(new Dimension(400, 34));
        presets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        presets.setAlignmentX(0);
        panel.add(presets);
        panel.add(Box.createVerticalStrut(14));
        panel.add(row("Lời nhắn · Tối đa 120 ký tự", message));
        panel.add(Box.createVerticalStrut(16));
        panel.add(Theme.label(giving ? "Chỉ chuyển khi cả hai đang ở cùng bàn chờ." : "Người được xin tự chọn đồng ý hoặc từ chối.", 11, Theme.MUTED));
        panel.add(Theme.label("Kiểm tra người nhận và số điểm trước khi xác nhận.", 11, Theme.MUTED));
        while (JOptionPane.showConfirmDialog(owner, panel, giving ? "Cho điểm" : "Xin điểm",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                BigDecimal value = PointUiRules.amount(amount.getText());
                if (message.getText().length() > 120) throw new IllegalArgumentException("Lời nhắn tối đa 120 ký tự.");
                Peer peer = (Peer) recipient.getSelectedItem();
                if (peer == null) return Optional.empty();
                if (giving && balance.compareTo(value) < 0) throw new IllegalArgumentException("Số dư của bạn không đủ.");
                String confirmation = giving ? PointUiRules.confirmation(peer.name(), peer.id(), value, balance)
                        : "Xin " + peer.name() + " (#" + peer.id() + ") " + Theme.points(value) + " điểm?";
                JTextArea plainText = new JTextArea(confirmation);
                plainText.setEditable(false);
                plainText.setOpaque(false);
                plainText.setForeground(Theme.TEXT);
                plainText.setFont(Theme.font(14, false));
                if (JOptionPane.showConfirmDialog(owner, plainText, "Xác nhận", JOptionPane.YES_NO_OPTION,
                        JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION) {
                    return Optional.of(new Entry(peer.id(), value, message.getText().trim()));
                }
            } catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(owner, e.getMessage(), "Kiểm tra thông tin", JOptionPane.INFORMATION_MESSAGE); }
        }
        return Optional.empty();
    }

    private static JPanel row(String title, JComponent field) {
        JPanel row = Theme.transparent(new BorderLayout(0, 6));
        JLabel label = Theme.label(title, 12, Theme.MUTED);
        label.setLabelFor(field);
        field.getAccessibleContext().setAccessibleName(title);
        row.add(label, BorderLayout.NORTH); row.add(field, BorderLayout.CENTER);
        row.setPreferredSize(new Dimension(400, 66));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        row.setAlignmentX(0);
        return row;
    }
}
