package com.game3cay.gui;

import com.game3cay.shared.model.RoomSummary;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.LongConsumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Exactly twenty server-owned tables, in stable numeric order. */
public final class LobbyPanel extends JPanel {
  private final TableCard[] tables = new TableCard[20];
  private final JLabel summary = Theme.label("Đang tải bàn chơi…", 14, Theme.MUTED);
  private LongConsumer join = id -> {};
  private boolean actions;

  public LobbyPanel() {
    super(new BorderLayout(0, 18));
    setOpaque(false);
    setBorder(new EmptyBorder(24, 32, 18, 32));
    var title = Theme.transparent(new BorderLayout());
    var copy = Theme.transparent(new GridLayout(0, 1, 0, 7));
    var eyebrow = Theme.label("SẢNH CHƠI / 20 BÀN", 11, Theme.GOLD);
    eyebrow.setFont(Theme.font(11, true));
    var h = Theme.label("Chọn bàn của bạn", 28, Theme.TEXT);
    h.setFont(Theme.font(28, true));
    copy.add(eyebrow);
    copy.add(h);
    copy.add(summary);
    title.add(copy, BorderLayout.WEST);
    var note = Theme.label("2–8 người / bàn     •     Chủ bàn chọn cược cơ bản", 13, Theme.MUTED);
    title.add(note, BorderLayout.EAST);
    add(title, BorderLayout.NORTH);
    var grid = new TableGrid();
    for (int i = 0; i < 20; i++) {
      tables[i] = new TableCard(i + 1);
      grid.add(tables[i]);
    }
    var scroll = new JScrollPane(grid);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setBorder(null);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(24);
    add(scroll, BorderLayout.CENTER);
    var footer = Theme.transparent(new BorderLayout());
    footer.add(
        Theme.label(
            "Chọn một bàn trống để mở cuộc chơi, hoặc vào bàn đang chờ để chơi cùng mọi người.",
            13,
            Theme.MUTED),
        BorderLayout.WEST);
    footer.add(Theme.label("● Có người    ○ Còn trống", 12, Theme.GOLD), BorderLayout.EAST);
    add(footer, BorderLayout.SOUTH);
  }

  public void onJoin(LongConsumer action) {
    join = action;
  }

  public void setRooms(List<RoomSummary> rooms) {
    var map = new HashMap<Long, RoomSummary>();
    rooms.forEach(r -> map.put(r.roomId(), r));
    int occupied = 0, people = 0;
    for (int i = 0; i < 20; i++) {
      var r = map.get((long) i + 1);
      tables[i].data = r;
      if (r != null) {
        people += r.playerCount();
        if (r.playerCount() > 0) occupied++;
      }
      tables[i].sync();
    }
    summary.setText(
        people
            + " người đang ngồi bàn  ·  "
            + occupied
            + " bàn có người  ·  "
            + (20 - occupied)
            + " bàn trống");
  }

  public void setActionsEnabled(boolean value) {
    actions = value;
    for (var t : tables) t.sync();
  }

  private static String compactBet(java.math.BigDecimal amount) {
    var thousand = new java.math.BigDecimal("1000");
    var million = new java.math.BigDecimal("1000000");
    var billion = new java.math.BigDecimal("1000000000");
    if (amount.compareTo(billion) >= 0)
      return Theme.points(amount.divide(billion, 1, java.math.RoundingMode.DOWN)) + " tỷ điểm";
    if (amount.compareTo(million) >= 0)
      return Theme.points(amount.divide(million, 1, java.math.RoundingMode.DOWN)) + "M điểm";
    if (amount.compareTo(thousand) >= 0)
      return Theme.points(amount.divide(thousand, 1, java.math.RoundingMode.DOWN)) + "K điểm";
    return Theme.points(amount) + " điểm";
  }

  private static final class TableGrid extends JPanel implements Scrollable {
    TableGrid() {
      super(new GridLayout(4, 5, 14, 14));
      setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(1000, 514);
    }

    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
      return 24;
    }

    public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
      return r.height - 24;
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return getParent() != null && getParent().getHeight() >= 514;
    }
  }

  private final class TableCard extends JButton {
    final int number;
    RoomSummary data;

    TableCard(int n) {
      number = n;
      setOpaque(false);
      setBorderPainted(false);
      setContentAreaFilled(false);
      setFocusPainted(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setToolTipText("Vào bàn " + n);
      getAccessibleContext().setAccessibleName("Bàn " + n);
      addActionListener(
          e -> {
            Sound.play("button_click");
            join.accept(number);
          });
    }

    void sync() {
      setEnabled(
          actions
              && data != null
              && "WAITING".equals(data.status())
              && data.playerCount() < data.maxPlayers());
      setToolTipText(
          data == null
              ? "Đang tải bàn " + number
              : "Bàn "
                  + number
                  + " · Cược cơ bản "
                  + Theme.points(data.betAmount())
                  + " điểm / người");
      repaint();
    }

    @Override
    protected void paintComponent(Graphics gg) {
      var g = (Graphics2D) gg.create();
      Assets.quality(g);
      int w = getWidth(), h = getHeight();
      boolean hover = getModel().isRollover() && isEnabled();
      g.setPaint(
          new GradientPaint(0, 0, new Color(16, 32, 56, 245), w, h, new Color(8, 18, 32, 250)));
      g.fillRoundRect(0, 0, w, h, 17, 17);
      g.setColor(hover ? Theme.GOLD : new Color(55, 78, 110));
      g.setStroke(new BasicStroke(hover ? 2f : 1f));
      g.drawRoundRect(1, 1, w - 3, h - 3, 17, 17);
      int n = data == null ? 0 : data.playerCount();
      boolean playing = data != null && "PLAYING".equals(data.status());
      boolean result = data != null && "RESULT".equals(data.status());
      g.setFont(Theme.font(20, true));
      g.setColor(Theme.TEXT);
      g.drawString("Bàn " + String.format("%02d", number), 17, 31);
      g.setFont(Theme.font(11, true));
      String tag =
          result ? "KẾT QUẢ" : playing ? "ĐANG CHƠI" : n == 8 ? "ĐÃ ĐẦY" : n > 0 ? "CÓ NGƯỜI" : "";
      g.setColor(playing || result ? Theme.GOLD : new Color(110, 190, 240));
      g.drawString(tag, w - 15 - g.getFontMetrics().stringWidth(tag), 29);
      int tw = Math.min(106, w / 2), th = Math.min(65, h - 60);
      g.drawImage(Assets.get("table.png", 240), 14, 39, tw, th, null);
      g.setFont(Theme.font(25, true));
      g.setColor(n > 0 ? Theme.TEXT : Theme.MUTED);
      g.drawString(n + "", w - 63, 70);
      g.setFont(Theme.font(12, false));
      g.setColor(Theme.MUTED);
      g.drawString("/ 8", w - 43, 70);
      for (int i = 0; i < 8; i++) {
        g.setColor(i < n ? Theme.GOLD : new Color(38, 58, 88));
        g.fillOval(w - 72 + (i % 4) * 12, 79 + (i / 4) * 12, 7, 7);
      }
      g.setColor(new Color(42, 65, 96));
      g.drawLine(16, h - 34, w - 16, h - 34);
      g.setFont(Theme.font(12, false));
      g.setColor(Theme.GOLD);
      g.drawString(data == null ? "—" : compactBet(data.betAmount()), 17, h - 13);
      g.setColor(hover ? Theme.GOLD : Theme.MUTED);
      String go =
          data == null
              ? "Đang tải…"
              : playing || result ? "Chờ ván sau" : n == 8 ? "Đủ người" : "Vào bàn →";
      g.drawString(go, w - 15 - g.getFontMetrics().stringWidth(go), h - 13);
      if (hasFocus()) {
        g.setColor(Theme.GOLD);
        g.drawRoundRect(4, 4, w - 9, h - 9, 14, 14);
      }
      g.dispose();
    }
  }
}
