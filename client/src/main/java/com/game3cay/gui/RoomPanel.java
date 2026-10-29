package com.game3cay.gui;

import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.RoomSnapshot;

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
    private final JTextField contentField = new Theme.HintField("Nhập tin nhắn…");
    private final JLabel chatEmpty = Theme.label("Chưa có tin nhắn. Chào mọi người nhé!", 11, Theme.MUTED);
    private final JLabel pointsHint = Theme.label("Dùng khi bàn đang chờ", 11, Theme.MUTED);

    private final JTextField betAmountField = new JTextField("10", 6);
    private final Theme.Button changeBetButton = new Theme.Button("Đổi cược", "plain");
    private final JPanel betControlsPanel = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));

    private final Theme.Button sendButton = new Theme.Button("Gửi", "gold");
    private final Theme.Button readyButton = new Theme.Button("Chơi tiếp", "plain");
    private final Theme.Button startButton = new Theme.Button("Bắt đầu", "gold");
    private final Theme.Button leaveButton = new Theme.Button("← Sảnh chơi", "plain");
    private final Theme.Button askPointsButton = new Theme.Button("Xin điểm", "plain");
    private final Theme.Button givePointsButton = new Theme.Button("Cho điểm", "plain");
    private final Theme.Button pointRequestsButton = new Theme.Button("Yêu cầu điểm", "plain");

    private final GamePanel gamePanel = new GamePanel();
    private RoomSnapshot roomSnapshot;
    private long currentUserId;
    private boolean areActionsEnabled;

    public RoomPanel() {
        super(new BorderLayout(0, 9));
        setOpaque(false);
        setBorder(new EmptyBorder(14, 22, 12, 22));

        readyButton.setPreferredSize(new Dimension(128, 38));
        startButton.setPreferredSize(new Dimension(160, 38));
        readyButton.setFont(Theme.font(13, true));
        startButton.setFont(Theme.font(13, true));
        gamePanel.setWaitingControls(readyButton, startButton);

        Theme.field(betAmountField);
        betAmountField.setPreferredSize(new Dimension(86, 42));
        betAmountField.setToolTipText("Cược cơ bản mỗi người góp đầu ván; tối đa 2 số thập phân");
        betAmountField.getAccessibleContext().setAccessibleName("Mức cược cơ bản của bàn");
        changeBetButton.setPreferredSize(new Dimension(104, 42));

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
        sidebarGlass.setPreferredSize(new Dimension(264, 500));

        var chatHeader = Theme.label("Trò chuyện trong bàn", 15, Theme.TEXT);
        chatHeader.setFont(Theme.font(15, true));
        sidebarGlass.add(chatHeader, BorderLayout.NORTH);
        var pointCard = new Theme.Glass(new BorderLayout(0, 9));
        pointCard.setBorder(new EmptyBorder(16, 15, 16, 15));
        var pointHeading = Theme.transparent(new GridLayout(0, 1, 0, 5));
        var pointTitle = Theme.label("Điểm bạn bè", 15, Theme.TEXT);
        pointTitle.setFont(Theme.font(15, true));
        pointHeading.add(pointTitle);
        pointHeading.add(pointsHint);
        pointCard.add(pointHeading, BorderLayout.NORTH);
        var pointActions = Theme.transparent(new GridLayout(1, 2, 6, 0));
        askPointsButton.setPreferredSize(new Dimension(85, 36));
        givePointsButton.setPreferredSize(new Dimension(85, 36));
        pointActions.add(askPointsButton);
        pointActions.add(givePointsButton);
        pointCard.add(pointActions, BorderLayout.CENTER);
        pointRequestsButton.setPreferredSize(new Dimension(180, 36));
        pointCard.add(pointRequestsButton, BorderLayout.SOUTH);

        chatPane.setEditable(false);
        chatPane.setOpaque(false);
        chatPane.setFont(Theme.font(13, false));
        chatPane.setForeground(Theme.TEXT);
        chatPane.setBorder(null);
        chatPane.getAccessibleContext().setAccessibleName("Tin nhắn trong bàn");

        var chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(null);
        chatScroll.setOpaque(false);
        chatScroll.getViewport().setOpaque(false);
        var chatBody = Theme.transparent(new BorderLayout(0, 10));
        chatBody.add(chatEmpty, BorderLayout.NORTH);
        chatBody.add(chatScroll, BorderLayout.CENTER);
        sidebarGlass.add(chatBody, BorderLayout.CENTER);

        var chatInputArea = Theme.transparent(new BorderLayout(8, 0));
        Theme.field(contentField);
        contentField.setToolTipText("Tin nhắn tối đa 500 ký tự (Enter để gửi)");
        chatInputArea.add(contentField, BorderLayout.CENTER);

        sendButton.setPreferredSize(new Dimension(57, 42));
        chatInputArea.add(sendButton, BorderLayout.EAST);

        sidebarGlass.add(chatInputArea, BorderLayout.SOUTH);
        var sidebar = Theme.transparent(new BorderLayout(0, 12));
        sidebar.setPreferredSize(new Dimension(264, 500));
        sidebar.add(sidebarGlass, BorderLayout.CENTER);
        sidebar.add(pointCard, BorderLayout.SOUTH);
        centralArea.add(sidebar, BorderLayout.EAST);
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

    public void onAskPoints(Runnable action) { askPointsButton.addActionListener(e -> action.run()); }
    public void onGivePoints(Runnable action) { givePointsButton.addActionListener(e -> action.run()); }
    public void onPointRequests(Runnable action) { pointRequestsButton.addActionListener(e -> action.run()); }
    public void pointRequestCount(int count) { pointRequestsButton.setText(count == 0 ? "Yêu cầu điểm" : "Yêu cầu điểm (" + count + ")"); }

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
        boolean hasPeer = roomSnapshot != null && roomSnapshot.players().stream().anyMatch(p -> p.userId() != currentUserId);
        askPointsButton.setEnabled(areActionsEnabled && isWaiting && hasPeer);
        givePointsButton.setEnabled(areActionsEnabled && isWaiting && hasPeer);
        askPointsButton.setToolTipText(isWaiting ? "Xin điểm từ người cùng bàn" : "Xin/cho điểm trong bàn chờ");
        givePointsButton.setToolTipText(isWaiting ? "Cho điểm cho người cùng bàn" : "Xin/cho điểm trong bàn chờ");
        pointRequestsButton.setEnabled(roomSnapshot != null);
        pointsHint.setText(!isWaiting ? "Khả dụng khi ván kết thúc" : hasPeer ? "Xin hoặc cho người cùng bàn" : "Chờ thêm người vào bàn");
        readyButton.putClientProperty("active", mySlot != null && !mySlot.ready());
        if (mySlot != null && mySlot.ready()) {
            readyButton.setText("Tạm nghỉ");
            readyButton.setEnabled(areActionsEnabled && isWaiting && "PLAYING".equals(mySlot.status()));
            readyButton.setToolTipText("Tạm dừng tham gia ván kế tiếp và hủy đếm ngược của bàn");
        } else {
            readyButton.setText("Chơi tiếp");
            readyButton.setEnabled(areActionsEnabled && isWaiting && mySlot != null && "PLAYING".equals(mySlot.status()));
            readyButton.setToolTipText("Xác nhận mức cược hiện tại và tham gia ván kế tiếp");
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
        chatEmpty.setVisible(false);
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
        chatEmpty.setVisible(true);
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
