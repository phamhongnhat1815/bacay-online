package com.game3cay.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.RoomSnapshot;

public final class RoomPanel extends JPanel {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final JLabel title = new JLabel();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{
                "Ghế", "ID", "Tên", "Vai trò",
                "Sẵn sàng", "Trạng thái"
            }, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTextArea chat = new JTextArea(9, 40);
    private final JTextField content = new JTextField();

    private final JButton send = new JButton("Gửi");
    private final JButton ready = new JButton("Sẵn sàng");
    private final JButton start = new JButton("Bắt đầu");
    private final JButton leave = new JButton("Rời phòng");

    private long roomId = -1;
    private boolean canReady;
    private boolean canStart;

    public RoomPanel() {
        super(new BorderLayout(8, 8));

        title.putClientProperty("html.disable", Boolean.TRUE);

        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ready);
        buttons.add(start);
        buttons.add(leave);
        header.add(buttons, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);

        chat.setEditable(false);
        chat.setLineWrap(true);
        chat.setWrapStyleWord(true);

        JPanel chatPanel = new JPanel(new BorderLayout(8, 8));
        chatPanel.setBorder(
                BorderFactory.createTitledBorder("Chat phòng")
        );
        chatPanel.add(new JScrollPane(chat), BorderLayout.CENTER);

        JPanel input = new JPanel(new BorderLayout(8, 8));
        input.add(content, BorderLayout.CENTER);
        input.add(send, BorderLayout.EAST);
        chatPanel.add(input, BorderLayout.SOUTH);

        add(chatPanel, BorderLayout.SOUTH);
        clear();
    }

    public void onReady(Runnable action) {
        ready.addActionListener(e -> action.run());
    }

    public void onStart(Runnable action) {
        start.addActionListener(e -> action.run());
    }

    public void onLeave(Runnable action) {
        leave.addActionListener(e -> action.run());
    }

    public void onSend(Runnable action) {
        send.addActionListener(e -> action.run());

        content.addActionListener(e -> {
            if (send.isEnabled()) {
                action.run();
            }
        });
    }

    public String messageText() {
        return content.getText().trim();
    }

    public void clearInput() {
        content.setText("");
    }

    public void render(RoomSnapshot room, long myId) {
        if (roomId != room.roomId()) {
            clear();
        }

        roomId = room.roomId();

        title.setText(
                room.roomName()
                + " | Mã: " + room.roomCode()
                + " | " + room.status()
                + " | " + room.players().size()
                + "/" + room.maxPlayers()
        );

        model.setRowCount(0);

        for (RoomSnapshot.PlayerSlot p : room.players()) {
            model.addRow(new Object[]{
                p.seatNumber(),
                p.userId(),
                p.displayName(),
                p.userId() == room.ownerUserId()
                        ? "Chủ phòng" : "Thành viên",
                p.ready() ? "Có" : "Chưa",
                p.status()
            });
        }

        var me = room.players().stream()
                .filter(p -> p.userId() == myId)
                .findFirst()
                .orElse(null);

        boolean waiting = "WAITING".equals(room.status());

        canReady = waiting
                && me != null
                && "PLAYING".equals(me.status());

        var active = room.players().stream()
                .filter(p -> "PLAYING".equals(p.status()))
                .toList();

        canStart = waiting
                && room.ownerUserId() == myId
                && active.size() >= room.minPlayers()
                && active.stream()
                        .allMatch(RoomSnapshot.PlayerSlot::ready);

        ready.setText(
                me != null && me.ready()
                        ? "Hủy sẵn sàng" : "Sẵn sàng"
        );

        start.setToolTipText(
                canStart
                        ? "Yêu cầu server bắt đầu ván"
                        : "Cần là chủ phòng, đủ người và tất cả người chơi sẵn sàng"
        );
    }

    public void appendChat(ChatMessage message) {
        if (chat.getDocument().getLength() > 30000) {
            chat.setText("");
        }

        String sender = "SYSTEM".equals(message.type())
                ? "Hệ thống"
                : message.senderDisplayName() == null
                        ? "Người chơi"
                        : message.senderDisplayName();

        chat.append(
                "[" + TIME.format(
                        Instant.ofEpochMilli(message.timestampMillis())
                ) + "] "
                + sender + ": " + message.content()
                + System.lineSeparator()
        );

        chat.setCaretPosition(chat.getDocument().getLength());
    }

    public void clear() {
        roomId = -1;
        canReady = false;
        canStart = false;

        title.setText("");
        model.setRowCount(0);
        chat.setText("");
        clearInput();

        setActionsEnabled(false);
    }

    public void setActionsEnabled(boolean enabled) {
        ready.setEnabled(enabled && canReady);
        start.setEnabled(enabled && canStart);
        leave.setEnabled(enabled && roomId > 0);
        send.setEnabled(enabled && roomId > 0);
        content.setEnabled(enabled && roomId > 0);
    }
}