package com.game3cay.admin.gui;

import com.game3cay.shared.model.AdminLiveMatchDetail;
import com.game3cay.shared.model.AdminLivePlayerHand;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.Suit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serial;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

public final class LiveRoomInspectorDialog extends JDialog {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final long roomId;
    private final Consumer<Long> onRefreshRequest;
    private final Consumer<Long> onResetRequest;

    private final JLabel titleLabel = AdminTheme.label("BÀN #—", 18, AdminTheme.GOLD, true);
    private final JLabel statusBadge = AdminTheme.label("WAITING", 12, AdminTheme.MUTED, true);
    private final JLabel anteBadge = AdminTheme.label("Cược sàn: 0", 12, AdminTheme.TEXT);
    private final JLabel potBadge = AdminTheme.label("Quỹ Pot: 0", 14, AdminTheme.GOLD, true);
    private final JLabel turnBadge = AdminTheme.label("Lượt đi: —", 12, AdminTheme.TEXT);
    private final JLabel roundIdLabel = AdminTheme.label("Mã ván: —", 11, AdminTheme.MUTED);

    private final JCheckBox autoRefreshCheck = new JCheckBox("Tự động làm mới (2s)", true);
    private final AdminTheme.Button refreshButton = new AdminTheme.Button("LÀM MỚI NGAY", "plain");
    private final AdminTheme.Button resetRoomButton = new AdminTheme.Button("GIẢI TÁN BÀN", "red");
    private final AdminTheme.Button closeButton = new AdminTheme.Button("ĐÓNG", "plain");
    private final JLabel lastUpdateLabel = AdminTheme.label("Chưa cập nhật", 11, AdminTheme.MUTED);

    private final JPanel playersContainer = new JPanel();
    private final JTextArea chatLogArea = new JTextArea();
    private Timer autoTimer;

    public LiveRoomInspectorDialog(Frame parent, long roomId, Consumer<Long> onRefreshRequest, Consumer<Long> onResetRequest) {
        super(parent, "[GOD-VIEW] Giám Sát Ván Bài Trực Tiếp - Bàn #" + roomId, false);
        this.roomId = roomId;
        this.onRefreshRequest = onRefreshRequest;
        this.onResetRequest = onResetRequest;

        setSize(1000, 680);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(parent);
        getContentPane().setBackground(AdminTheme.BG);
        setLayout(new BorderLayout(0, 12));

        buildHeader();
        buildCenter();
        buildFooter();

        autoTimer = new Timer(2000, e -> {
            if (autoRefreshCheck.isSelected() && onRefreshRequest != null) {
                onRefreshRequest.accept(roomId);
            }
        });
        autoTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopTimer();
            }
        });
    }

    private void buildHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
        headerPanel.setBackground(AdminTheme.PANEL);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AdminTheme.BORDER),
                new EmptyBorder(14, 20, 14, 20)
        ));

        JPanel topInfo = AdminTheme.transparent(new BorderLayout());
        JPanel titleBox = AdminTheme.transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleBox.add(titleLabel);
        titleBox.add(statusBadge);
        titleBox.add(anteBadge);
        titleBox.add(potBadge);
        titleBox.add(turnBadge);
        topInfo.add(titleBox, BorderLayout.WEST);

        JPanel actions = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        autoRefreshCheck.setFont(AdminTheme.font(12, false));
        autoRefreshCheck.setForeground(AdminTheme.TEXT);
        autoRefreshCheck.setOpaque(false);
        autoRefreshCheck.setFocusPainted(false);
        actions.add(autoRefreshCheck);
        actions.add(refreshButton);
        actions.add(resetRoomButton);
        topInfo.add(actions, BorderLayout.EAST);

        headerPanel.add(topInfo, BorderLayout.NORTH);
        headerPanel.add(roundIdLabel, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> {
            if (onRefreshRequest != null) {
                onRefreshRequest.accept(roomId);
            }
        });

        resetRoomButton.addActionListener(e -> {
            if (onResetRequest != null) {
                onResetRequest.accept(roomId);
            }
        });

        add(headerPanel, BorderLayout.NORTH);
    }

    private void buildCenter() {
        JPanel centerSplit = AdminTheme.transparent(new BorderLayout(14, 0));
        centerSplit.setBorder(new EmptyBorder(0, 20, 0, 20));

        playersContainer.setLayout(new BoxLayout(playersContainer, BoxLayout.Y_AXIS));
        playersContainer.setBackground(AdminTheme.BG);

        JScrollPane playersScroll = new JScrollPane(playersContainer);
        playersScroll.getViewport().setBackground(AdminTheme.BG);
        playersScroll.setBorder(BorderFactory.createLineBorder(AdminTheme.BORDER));
        playersScroll.getVerticalScrollBar().setUnitIncrement(16);
        centerSplit.add(playersScroll, BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout(0, 8));
        chatPanel.setPreferredSize(new Dimension(340, 400));
        chatPanel.setBackground(AdminTheme.PANEL);
        chatPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AdminTheme.BORDER),
                new EmptyBorder(12, 12, 12, 12)
        ));

        chatPanel.add(AdminTheme.label("NHẬT KÝ CHAT & SỰ KIỆN PHÒNG", 12, AdminTheme.GOLD, true), BorderLayout.NORTH);

        chatLogArea.setEditable(false);
        chatLogArea.setLineWrap(true);
        chatLogArea.setWrapStyleWord(true);
        chatLogArea.setBackground(new Color(10, 20, 35));
        chatLogArea.setForeground(AdminTheme.TEXT);
        chatLogArea.setFont(AdminTheme.font(12, false));
        chatLogArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane chatScroll = new JScrollPane(chatLogArea);
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(25, 42, 68)));
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        centerSplit.add(chatPanel, BorderLayout.EAST);

        add(centerSplit, BorderLayout.CENTER);
    }

    private void buildFooter() {
        JPanel footer = AdminTheme.transparent(new BorderLayout());
        footer.setBorder(new EmptyBorder(10, 20, 14, 20));
        footer.add(lastUpdateLabel, BorderLayout.WEST);

        closeButton.addActionListener(e -> {
            stopTimer();
            dispose();
        });
        footer.add(closeButton, BorderLayout.EAST);

        add(footer, BorderLayout.SOUTH);
    }

    public void updateMatchDetail(AdminLiveMatchDetail detail) {
        if (detail == null) return;

        titleLabel.setText("BÀN #" + detail.roomId() + ": " + detail.roomName());

        String status = detail.status();
        statusBadge.setText(status);
        if ("PLAYING".equals(status)) {
            statusBadge.setForeground(AdminTheme.GREEN);
        } else if ("RESULT".equals(status)) {
            statusBadge.setForeground(AdminTheme.GOLD);
        } else {
            statusBadge.setForeground(AdminTheme.MUTED);
        }

        anteBadge.setText("Cược sàn: " + AdminTheme.points(detail.ante()));
        potBadge.setText("Tổng POT: " + AdminTheme.points(detail.currentPot()));

        if (detail.currentTurnDisplayName() != null && !detail.currentTurnDisplayName().isBlank()) {
            turnBadge.setText("Lượt đi: " + detail.currentTurnDisplayName());
            turnBadge.setForeground(AdminTheme.GOLD);
        } else {
            turnBadge.setText("Lượt đi: —");
            turnBadge.setForeground(AdminTheme.TEXT);
        }

        roundIdLabel.setText("Mã ván: " + (detail.roundId() != null ? detail.roundId() : "Chưa bắt đầu")
                + " · Số người: " + detail.playerCount() + "/" + detail.maxPlayers()
                + " · Vòng cược: " + detail.stage());

        lastUpdateLabel.setText("Cập nhật lúc " + LocalTime.now().format(TIME_FORMATTER));

        playersContainer.removeAll();
        List<AdminLivePlayerHand> players = detail.players();
        if (players == null || players.isEmpty()) {
            JPanel emptyBox = AdminTheme.transparent(new FlowLayout(FlowLayout.CENTER, 0, 40));
            emptyBox.add(AdminTheme.label("Hiện không có người chơi nào ngồi tại bàn này.", 14, AdminTheme.MUTED));
            playersContainer.add(emptyBox);
        } else {
            for (AdminLivePlayerHand p : players) {
                playersContainer.add(createPlayerBox(p, detail.currentTurnUserId()));
                playersContainer.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }
        playersContainer.revalidate();
        playersContainer.repaint();

        StringBuilder sb = new StringBuilder();
        if (detail.recentChats() != null) {
            for (ChatMessage m : detail.recentChats()) {
                String time = Instant.ofEpochMilli(m.timestampMillis())
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(TIME_FORMATTER);
                if ("SYSTEM".equalsIgnoreCase(m.type())) {
                    sb.append("[").append(time).append("] [HỆ THỐNG] ").append(m.content()).append("\n");
                } else {
                    sb.append("[").append(time).append("] ").append(m.senderDisplayName()).append(": ").append(m.content()).append("\n");
                }
            }
        }
        chatLogArea.setText(sb.toString());
        chatLogArea.setCaretPosition(chatLogArea.getDocument().getLength());
    }

    private JPanel createPlayerBox(AdminLivePlayerHand p, Long currentTurnId) {
        boolean isTurn = currentTurnId != null && currentTurnId == p.userId();

        JPanel box = new JPanel(new BorderLayout(12, 6));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));
        box.setPreferredSize(new Dimension(580, 105));
        box.setBackground(isTurn ? new Color(22, 42, 70) : AdminTheme.PANEL);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isTurn ? AdminTheme.GOLD : AdminTheme.BORDER, isTurn ? 2 : 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JPanel left = AdminTheme.transparent(new GridLayout(0, 1, 0, 3));
        String title = "Ghế #" + p.seat() + " · " + p.displayName() + " (" + p.username() + ")";
        JLabel nameLabel = AdminTheme.label(title, 13, isTurn ? AdminTheme.GOLD : AdminTheme.TEXT, true);
        left.add(nameLabel);

        String sub = "Số dư: " + AdminTheme.points(p.balance()) + " | Đã cược: " + AdminTheme.points(p.currentBet());
        left.add(AdminTheme.label(sub, 11, AdminTheme.MUTED));

        String st = p.status();
        Color stColor = switch (st) {
            case "ACTIVE" -> AdminTheme.GREEN;
            case "ALL_IN" -> AdminTheme.GOLD;
            case "FOLDED" -> AdminTheme.RED;
            case "READY" -> new Color(80, 200, 220);
            default -> AdminTheme.MUTED;
        };
        JLabel statusLbl = AdminTheme.label("Trạng thái: " + st + (isTurn ? " [ĐANG ĐẾN LƯỢT]" : ""), 11, stColor, true);
        left.add(statusLbl);
        box.add(left, BorderLayout.WEST);

        JPanel cardsArea = AdminTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        List<Card> cards = p.cards();
        if (cards != null && !cards.isEmpty()) {
            for (Card c : cards) {
                cardsArea.add(createCardComponent(c));
            }
        } else if ("FOLDED".equals(st)) {
            cardsArea.add(AdminTheme.label("[ ĐÃ ÚP BÀI ]", 13, AdminTheme.RED, true));
        } else {
            cardsArea.add(AdminTheme.label("[ CHỜ CHIA BÀI ]", 12, AdminTheme.MUTED));
        }

        if (p.evaluation() != null && !p.evaluation().isBlank()) {
            JPanel evalBadge = new JPanel(new BorderLayout());
            evalBadge.setBackground(new Color(15, 32, 54));
            evalBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AdminTheme.GOLD, 1),
                    new EmptyBorder(4, 8, 4, 8)
            ));
            JLabel evalLabel = AdminTheme.label(p.evaluation(), 12, AdminTheme.GOLD, true);
            evalBadge.add(evalLabel, BorderLayout.CENTER);
            cardsArea.add(evalBadge);
        }

        box.add(cardsArea, BorderLayout.EAST);
        return box;
    }

    private JPanel createCardComponent(Card c) {
        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(42, 60));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160, 175, 195), 1),
                new EmptyBorder(2, 2, 2, 2)
        ));

        boolean isRed = c.suit() == Suit.CO || c.suit() == Suit.RO;
        Color cardColor = isRed ? new Color(210, 25, 25) : new Color(20, 20, 20);

        JLabel text = new JLabel(c.rank().displayName() + c.suit().symbol(), SwingConstants.CENTER);
        text.setFont(new Font("Segoe UI", Font.BOLD, 13));
        text.setForeground(cardColor);
        card.add(text, BorderLayout.CENTER);

        return card;
    }

    public void stopTimer() {
        if (autoTimer != null) {
            autoTimer.stop();
            autoTimer = null;
        }
    }
}
