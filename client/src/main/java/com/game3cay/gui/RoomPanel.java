package com.game3cay.gui;

import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.RoomSnapshot;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public final class RoomPanel extends JPanel {
    private final JLabel titleLabel = Theme.label("", 21, Theme.TEXT);
    private final JLabel detailLabel = Theme.label("", 12, Theme.MUTED);

    private final JTextPane chatPane = new JTextPane();
    private final JTextField contentField = new JTextField();

    private final JTextField betAmountField = new JTextField("10", 6);
    private final Theme.Button changeBetButton = new Theme.Button("Đổi cược", "gold");
    private final JPanel betControlsPanel = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));

    private final Theme.Button sendButton = new Theme.Button("Gửi", "gold");
    private final Theme.Button readyButton = new Theme.Button("Sẵn sàng", "gold");
    private final Theme.Button startButton = new Theme.Button("Bắt đầu", "gold");
    private final Theme.Button leaveButton = new Theme.Button("← Sảnh chơi", "plain");

    private final GamePanel gamePanel = new GamePanel();
    private RoomSnapshot roomSnapshot;
    private long currentUserId;
    private boolean areActionsEnabled;

    public RoomPanel() {
        super(new BorderLayout(0, 9));
        setOpaque(false);
        setBorder(new EmptyBorder(14, 22, 12, 22));

        readyButton.setPreferredSize(new Dimension(145, 44));
        startButton.setPreferredSize(new Dimension(105, 44));
        gamePanel.setWaitingControls(readyButton, startButton);

        Theme.field(betAmountField);
        betAmountField.setPreferredSize(new Dimension(86, 42));
        betAmountField.setToolTipText("Cược cơ bản mỗi người góp đầu ván; tối đa 2 số thập phân");

        betControlsPanel.add(Theme.label("Cược cơ bản", 12, Theme.MUTED));
        betControlsPanel.add(betAmountField);
        betControlsPanel.add(changeBetButton);

        leaveButton.setPreferredSize(new Dimension(145, 44));

        var headerPanel = Theme.transparent(new BorderLayout(18, 0));
        var leftHeader = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftHeader.add(leaveButton);

        var roomTitles = Theme.transparent(new GridLayout(0, 1, 0, 3));
        titleLabel.setFont(Theme.font(21, true));
        roomTitles.add(titleLabel);
        roomTitles.add(detailLabel);
        leftHeader.add(roomTitles);
        headerPanel.add(leftHeader, BorderLayout.WEST);

        var rightHeader = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        rightHeader.add(betControlsPanel);
        headerPanel.add(rightHeader, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        var centralArea = Theme.transparent(new BorderLayout(12, 0));
        centralArea.add(gamePanel, BorderLayout.CENTER);

        var sidebarGlass = new Theme.Glass(new BorderLayout(0, 12));
        sidebarGlass.setBorder(new EmptyBorder(18, 15, 15, 15));
        sidebarGlass.setPreferredSize(new Dimension(220, 500));

        var chatHeader = Theme.label("TRÒ CHUYỆN", 12, Theme.GOLD);
        chatHeader.setFont(Theme.font(12, true));
        sidebarGlass.add(chatHeader, BorderLayout.NORTH);

        chatPane.setEditable(false);
        chatPane.setOpaque(false);
        chatPane.setFont(Theme.font(13, false));
        chatPane.setForeground(Theme.TEXT);
        chatPane.setBorder(null);

        var chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(null);
        chatScroll.setOpaque(false);
        chatScroll.getViewport().setOpaque(false);
        sidebarGlass.add(chatScroll, BorderLayout.CENTER);

        var chatInputArea = Theme.transparent(new BorderLayout(0, 8));
        Theme.field(contentField);
        contentField.setToolTipText("Tin nhắn tối đa 500 ký tự (Enter để gửi)");
        chatInputArea.add(contentField, BorderLayout.NORTH);

        sendButton.setPreferredSize(new Dimension(110, 39));
        chatInputArea.add(sendButton, BorderLayout.CENTER);
        chatInputArea.add(Theme.label("Lịch sự để cuộc vui trọn vẹn.", 11, Theme.MUTED), BorderLayout.SOUTH);

        sidebarGlass.add(chatInputArea, BorderLayout.SOUTH);
        centralArea.add(sidebarGlass, BorderLayout.EAST);
        add(centralArea, BorderLayout.CENTER);

        clear();
    }

    public void onChangeBet(Consumer<BigDecimal> action) {
        changeBetButton.addActionListener(e -> {
            try {
                BigDecimal amount = new BigDecimal(betAmountField.getText().trim());
                if (amount.signum() <= 0
                        || amount.scale() < 0
                        || amount.scale() > 2
                        || amount.precision() > 15) {
                    throw new NumberFormatException();
                }
                action.accept(amount);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Vui lòng nhập mức cược lớn hơn 0, tối đa 2 chữ số thập phân (dùng dấu chấm).",
                        "Cược cơ bản",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });

        betAmountField.addActionListener(e -> {
            if (changeBetButton.isEnabled()) {
                changeBetButton.doClick();
            }
        });
    }

    public void onReady(Runnable runnable) {
        readyButton.addActionListener(e -> runnable.run());
    }

    public void onStart(Runnable runnable) {
        startButton.addActionListener(e -> runnable.run());
    }

    public void onLeave(Runnable runnable) {
        leaveButton.addActionListener(e -> runnable.run());
    }

    public void onSend(Runnable runnable) {
        sendButton.addActionListener(e -> runnable.run());
        contentField.addActionListener(e -> {
            if (sendButton.isEnabled()) {
                runnable.run();
            }
        });
    }

    public String messageText() {
        return contentField.getText().trim();
    }

    public void clearInput() {
        contentField.setText("");
    }

    public void render(RoomSnapshot room, long userId) {
        boolean isNewRoom = (roomSnapshot == null) || (roomSnapshot.roomId() != room.roomId());
        boolean isNewBet = (roomSnapshot == null) || (roomSnapshot.betAmount().compareTo(room.betAmount()) != 0);

        if (isNewRoom) {
            clear();
        }
        if (isNewRoom || isNewBet || !betAmountField.hasFocus()) {
            betAmountField.setText(room.betAmount().stripTrailingZeros().toPlainString());
        }

        roomSnapshot = room;
        currentUserId = userId;

        titleLabel.setText(room.roomName());
        detailLabel.setText(
                room.players().size() + " / " + room.maxPlayers() + " người  ·  "
                        + Theme.points(room.betAmount()) + " điểm cơ bản"
        );

        gamePanel.room(room, userId);
        syncButtons();
    }

    private void syncButtons() {
        RoomSnapshot.PlayerSlot mySlot = null;
        if (roomSnapshot != null) {
            mySlot = roomSnapshot.players().stream()
                    .filter(p -> p.userId() == currentUserId)
                    .findFirst()
                    .orElse(null);
        }

        boolean isWaiting = (roomSnapshot != null) && "WAITING".equals(roomSnapshot.status());
        if (mySlot != null && mySlot.ready()) {
            readyButton.setText("✅ Đã sẵn sàng");
            readyButton.setEnabled(areActionsEnabled && isWaiting && "PLAYING".equals(mySlot.status()));
            readyButton.setToolTipText("Bạn đang ở trạng thái sẵn sàng (bấm nếu muốn tạm dừng / hủy sẵn sàng)");
        } else {
            readyButton.setText("Sẵn sàng");
            readyButton.setEnabled(areActionsEnabled && isWaiting && mySlot != null && "PLAYING".equals(mySlot.status()));
            readyButton.setToolTipText("Xác nhận sẵn sàng vào ván");
        }

        boolean isOwner = (roomSnapshot != null) && (roomSnapshot.ownerUserId() == currentUserId);
        startButton.setVisible(isOwner);
        betControlsPanel.setVisible(isOwner);
        betAmountField.setEnabled(areActionsEnabled && isOwner && isWaiting);
        changeBetButton.setEnabled(areActionsEnabled && isOwner && isWaiting);

        List<RoomSnapshot.PlayerSlot> activePlayers = (roomSnapshot == null)
                ? List.of()
                : roomSnapshot.players().stream().filter(p -> "PLAYING".equals(p.status())).toList();

        boolean canStart = areActionsEnabled
                && isWaiting
                && isOwner
                && activePlayers.size() >= roomSnapshot.minPlayers()
                && activePlayers.stream().allMatch(RoomSnapshot.PlayerSlot::ready);

        startButton.setEnabled(canStart);
        if (roomSnapshot != null && roomSnapshot.countdownSeconds() > 0) {
            startButton.setText("Bắt đầu ngay (" + roomSnapshot.countdownSeconds() + "s)");
        } else {
            startButton.setText("Bắt đầu ngay");
        }
        startButton.setToolTipText("Bấm để bắt đầu ván ngay lập tức mà không cần chờ hết đếm ngược");

        leaveButton.setEnabled(areActionsEnabled && roomSnapshot != null);
        sendButton.setEnabled(areActionsEnabled && roomSnapshot != null);

        contentField.setEnabled(areActionsEnabled && roomSnapshot != null);
        gamePanel.setActionsEnabled(areActionsEnabled && roomSnapshot != null);
    }

    public void appendChat(ChatMessage message) {
        if (message == null || "SYSTEM".equalsIgnoreCase(message.type()) || message.senderId() == 0L) {
            return;
        }
        StyledDocument doc = chatPane.getStyledDocument();
        try {
            if (doc.getLength() > 25000) {
                doc.remove(0, Math.min(6000, doc.getLength()));
            }

            SimpleAttributeSet nameStyle = new SimpleAttributeSet();
            StyleConstants.setFontFamily(nameStyle, "SansSerif");
            StyleConstants.setFontSize(nameStyle, 12);
            StyleConstants.setForeground(nameStyle, Theme.GOLD);
            StyleConstants.setBold(nameStyle, true);

            String sender = (message.senderDisplayName() == null || message.senderDisplayName().isBlank()) ? "Người chơi" : message.senderDisplayName();

            String timeString = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(message.timestampMillis()));

            doc.insertString(doc.getLength(), sender + "  " + timeString + "\n", nameStyle);

            SimpleAttributeSet textStyle = new SimpleAttributeSet();
            StyleConstants.setFontFamily(textStyle, "SansSerif");
            StyleConstants.setFontSize(textStyle, 13);
            StyleConstants.setForeground(textStyle, Theme.TEXT);

            doc.insertString(doc.getLength(), message.content() + "\n\n", textStyle);
            chatPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    public void clear() {
        roomSnapshot = null;
        chatPane.setText("");
        clearInput();
        gamePanel.clear();
        areActionsEnabled = false;
        titleLabel.setText("");
        detailLabel.setText("");
        syncButtons();
    }

    public GamePanel game() {
        return gamePanel;
    }

    public void setActionsEnabled(boolean enabled) {
        this.areActionsEnabled = enabled;
        syncButtons();
    }
}
