package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;

public final class RoomPanel extends JPanel {
  private final JLabel title = Theme.label("", 21, Theme.TEXT),
      detail = Theme.label("", 12, Theme.MUTED);
  private final JTextPane chat = new JTextPane();
  private final JTextField content = new JTextField();
  private final JTextField betAmount = new JTextField("10", 6);
  private final Theme.Button changeBet = new Theme.Button("Đổi cược", "gold");
  private final JPanel betControls = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
  private final Theme.Button send = new Theme.Button("Gửi", "gold"),
      ready = new Theme.Button("Sẵn sàng", "gold"),
      start = new Theme.Button("Bắt đầu", "gold"),
      leave = new Theme.Button("← Sảnh chơi", "plain");
  private final GamePanel game = new GamePanel();
  private RoomSnapshot snapshot;
  private long myId;
  private boolean enabled;

  public RoomPanel() {
    super(new BorderLayout(0, 9));
    setOpaque(false);
    setBorder(new EmptyBorder(14, 22, 12, 22));
    ready.setPreferredSize(new Dimension(145, 44));
    start.setPreferredSize(new Dimension(105, 44));
    game.setWaitingControls(ready, start);
    Theme.field(betAmount);
    betAmount.setPreferredSize(new Dimension(86, 42));
    betAmount.setToolTipText("Cược cơ bản mỗi người góp đầu ván; tối đa 2 số thập phân");
    betControls.add(Theme.label("Cược cơ bản", 12, Theme.MUTED));
    betControls.add(betAmount);
    betControls.add(changeBet);
    leave.setPreferredSize(new Dimension(145, 44));
    var header = Theme.transparent(new BorderLayout(18, 0));
    var left = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
    left.add(leave);
    var labels = Theme.transparent(new GridLayout(0, 1, 0, 3));
    title.setFont(Theme.font(21, true));
    labels.add(title);
    labels.add(detail);
    left.add(labels);
    header.add(left, BorderLayout.WEST);
    var buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 9, 0));
    buttons.add(betControls);
    header.add(buttons, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);
    var area = Theme.transparent(new BorderLayout(12, 0));
    area.add(game, BorderLayout.CENTER);
    var sidebar = new Theme.Glass(new BorderLayout(0, 12));
    sidebar.setBorder(new EmptyBorder(18, 15, 15, 15));
    sidebar.setPreferredSize(new Dimension(220, 500));
    var ch = Theme.label("TRÒ CHUYỆN", 12, Theme.GOLD);
    ch.setFont(Theme.font(12, true));
    sidebar.add(ch, BorderLayout.NORTH);
    chat.setEditable(false);
    chat.setOpaque(false);
    chat.setFont(Theme.font(13, false));
    chat.setForeground(Theme.TEXT);
    chat.setBorder(null);
    var scroll = new JScrollPane(chat);
    scroll.setBorder(null);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    sidebar.add(scroll, BorderLayout.CENTER);
    var bottom = Theme.transparent(new BorderLayout(0, 10));
    Theme.field(content);
    content.setToolTipText("Tin nhắn tối đa 500 ký tự");
    bottom.add(content, BorderLayout.NORTH);
    send.setPreferredSize(new Dimension(110, 39));
    bottom.add(send, BorderLayout.CENTER);
    bottom.add(Theme.label("Lịch sự để cuộc vui trọn vẹn.", 11, Theme.MUTED), BorderLayout.SOUTH);
    sidebar.add(bottom, BorderLayout.SOUTH);
    area.add(sidebar, BorderLayout.EAST);
    add(area, BorderLayout.CENTER);
    clear();
  }

  public void onChangeBet(java.util.function.Consumer<java.math.BigDecimal> action) {
    changeBet.addActionListener(
        e -> {
          try {
            var amount = new java.math.BigDecimal(betAmount.getText().trim());
            if (amount.signum() <= 0
                || amount.scale() < 0
                || amount.scale() > 2
                || amount.precision() > 15) throw new NumberFormatException();
            action.accept(amount);
          } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Nhập cược lớn hơn 0, tối đa 2 số thập phân; dùng dấu chấm.",
                "Cược cơ bản",
                JOptionPane.INFORMATION_MESSAGE);
          }
        });
    betAmount.addActionListener(
        e -> {
          if (changeBet.isEnabled()) changeBet.doClick();
        });
  }

  public void onReady(Runnable r) {
    ready.addActionListener(e -> r.run());
  }

  public void onStart(Runnable r) {
    start.addActionListener(e -> r.run());
  }

  public void onLeave(Runnable r) {
    leave.addActionListener(e -> r.run());
  }

  public void onSend(Runnable r) {
    send.addActionListener(e -> r.run());
    content.addActionListener(
        e -> {
          if (send.isEnabled()) r.run();
        });
  }

  public String messageText() {
    return content.getText().trim();
  }

  public void clearInput() {
    content.setText("");
  }

  public void render(RoomSnapshot room, long uid) {
    boolean newTable = snapshot == null || snapshot.roomId() != room.roomId();
    boolean newBet = snapshot == null || snapshot.betAmount().compareTo(room.betAmount()) != 0;
    if (newTable) clear();
    if (newTable || newBet || !betAmount.hasFocus())
      betAmount.setText(room.betAmount().stripTrailingZeros().toPlainString());
    snapshot = room;
    myId = uid;
    title.setText(room.roomName());
    detail.setText(
        room.players().size()
            + " / "
            + room.maxPlayers()
            + " người  ·  "
            + Theme.points(room.betAmount())
            + " điểm cơ bản");
    game.room(room, uid);
    buttons();
  }

  private void buttons() {
    var me =
        snapshot == null
            ? null
            : snapshot.players().stream().filter(p -> p.userId() == myId).findFirst().orElse(null);
    boolean waiting = snapshot != null && "WAITING".equals(snapshot.status());
    ready.setText(me != null && me.ready() ? "Hủy sẵn sàng" : "Sẵn sàng");
    ready.setEnabled(enabled && waiting && me != null && "PLAYING".equals(me.status()));
    boolean owner = snapshot != null && snapshot.ownerUserId() == myId;
    start.setVisible(owner);
    betControls.setVisible(owner);
    betAmount.setEnabled(enabled && owner && waiting);
    changeBet.setEnabled(enabled && owner && waiting);
    var active =
        snapshot == null
            ? java.util.List.<RoomSnapshot.PlayerSlot>of()
            : snapshot.players().stream().filter(p -> "PLAYING".equals(p.status())).toList();
    start.setEnabled(
        enabled
            && waiting
            && owner
            && active.size() >= snapshot.minPlayers()
            && active.stream().allMatch(RoomSnapshot.PlayerSlot::ready));
    start.setToolTipText("Cần ít nhất 2 người đủ điểm và tất cả đã sẵn sàng");
    leave.setEnabled(enabled && snapshot != null);
    send.setEnabled(enabled && snapshot != null);
    content.setEnabled(enabled && snapshot != null);
    game.setActionsEnabled(enabled && snapshot != null);
  }

  public void appendChat(ChatMessage m) {
    var doc = chat.getStyledDocument();
    try {
      if (doc.getLength() > 25000) doc.remove(0, Math.min(6000, doc.getLength()));
      var name = new SimpleAttributeSet();
      StyleConstants.setFontFamily(name, "SansSerif");
      StyleConstants.setFontSize(name, 12);
      StyleConstants.setForeground(name, "SYSTEM".equals(m.type()) ? Theme.MUTED : Theme.GOLD);
      StyleConstants.setBold(name, true);
      String sender =
          "SYSTEM".equals(m.type())
              ? "Bàn chơi"
              : m.senderDisplayName() == null ? "Người chơi" : m.senderDisplayName();
      doc.insertString(
          doc.getLength(),
          sender
              + "  "
              + DateTimeFormatter.ofPattern("HH:mm")
                  .withZone(ZoneId.systemDefault())
                  .format(Instant.ofEpochMilli(m.timestampMillis()))
              + "\n",
          name);
      var text = new SimpleAttributeSet();
      StyleConstants.setFontFamily(text, "SansSerif");
      StyleConstants.setFontSize(text, 13);
      StyleConstants.setForeground(text, "SYSTEM".equals(m.type()) ? Theme.MUTED : Theme.TEXT);
      doc.insertString(doc.getLength(), m.content() + "\n\n", text);
      chat.setCaretPosition(doc.getLength());
    } catch (BadLocationException e) {
      throw new IllegalStateException(e);
    }
  }

  public void clear() {
    snapshot = null;
    chat.setText("");
    clearInput();
    game.clear();
    enabled = false;
    title.setText("");
    detail.setText("");
    buttons();
  }

  public GamePanel game() {
    return game;
  }

  public void setActionsEnabled(boolean value) {
    enabled = value;
    buttons();
  }
}
