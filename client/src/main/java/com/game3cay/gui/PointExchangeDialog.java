package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Requests and transfer history remain nonmodal while normal table play continues. */
public final class PointExchangeDialog extends JDialog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());
    private final DefaultTableModel requests = rows("Thời gian", "Chiều", "Người chơi", "Điểm", "Lời nhắn", "Trạng thái", "Còn lại");
    private final DefaultTableModel history = rows("Thời gian", "Chiều", "Người cho", "Người nhận", "Điểm", "Lời nhắn", "Mã giao dịch");
    private final PlainTable requestTable = new PlainTable(requests);
    private final JLabel status = Theme.label("Đang tải yêu cầu…", 12, Theme.MUTED);
    private final JLabel requestPages = Theme.label("", 12, Theme.MUTED);
    private final JLabel historyPages = Theme.label("", 12, Theme.MUTED);
    private final Theme.Button accept = new Theme.Button("Cho điểm", "gold");
    private final Theme.Button decline = new Theme.Button("Từ chối", "plain");
    private final Theme.Button cancel = new Theme.Button("Hủy yêu cầu", "plain");
    private final Theme.Button block = new Theme.Button("Bỏ qua người này", "plain");
    private final Theme.Button retry = new Theme.Button("Xác minh lại", "gold");
    private final Theme.Button requestPrevious = new Theme.Button("← Trước", "plain");
    private final Theme.Button requestNext = new Theme.Button("Sau →", "plain");
    private final Theme.Button historyPrevious = new Theme.Button("← Trước", "plain");
    private final Theme.Button historyNext = new Theme.Button("Sau →", "plain");
    private final JTabbedPane tabs = new JTabbedPane();
    private final Timer clock = new Timer(1000, e -> updateClock());
    private List<PointRequestItem> pageItems = List.of();
    private Set<Long> ignored = Set.of();
    private RoomSnapshot room;
    private long userId;
    private java.math.BigDecimal balance = java.math.BigDecimal.ZERO;
    private boolean busy;
    private int requestPage;
    private int requestTotalPages;
    private long requestTotalItems;
    private int historyPage;
    private int historyTotalPages;
    private BiConsumer<PointRequestItem, PointRequestDecision> reply = (r, d) -> {};
    private LongConsumer blockAction = id -> {};
    private IntConsumer requestLoader = page -> {};
    private IntConsumer historyLoader = page -> {};
    private Runnable retryAction = () -> {};

    public PointExchangeDialog(JFrame owner) {
        super(owner, "Xin / Cho điểm", false);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel heading = Theme.transparent(new GridLayout(0, 1, 0, 5));
        heading.add(Theme.label("Điểm bạn bè", 26, Theme.TEXT));
        heading.add(Theme.label("Chỉ cho điểm khi cả hai đang cùng bàn chờ. Gửi yêu cầu không làm thay đổi số dư.", 12, Theme.MUTED));
        root.add(heading, BorderLayout.NORTH);
        JPanel requestPanel = Theme.transparent(new BorderLayout(0, 9));
        requestPanel.setBorder(new EmptyBorder(12, 0, 0, 0));
        requestTable.emptyText("Đang tải yêu cầu điểm…");
        requestPanel.add(scroll(requestTable), BorderLayout.CENTER);
        JPanel requestActions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        for (JButton button : new JButton[]{block, cancel, decline, accept}) requestActions.add(button);
        requestPanel.add(requestActions, BorderLayout.NORTH);
        JPanel requestNav = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        Theme.Button refreshRequests = new Theme.Button("Làm mới", "plain");
        requestNav.add(requestPages); requestNav.add(requestPrevious); requestNav.add(requestNext); requestNav.add(refreshRequests);
        requestPanel.add(requestNav, BorderLayout.SOUTH);
        JPanel historyPanel = Theme.transparent(new BorderLayout(0, 9));
        historyPanel.setBorder(new EmptyBorder(12, 0, 0, 0));
        var historyTable = new PlainTable(history);
        historyTable.emptyText("Bạn chưa cho hoặc nhận điểm");
        historyPanel.add(scroll(historyTable), BorderLayout.CENTER);
        columns(requestTable, 125, 110, 180, 85, 180, 120, 80);
        columns(historyTable, 125, 90, 180, 180, 85, 180, 250);
        JPanel historyNav = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        Theme.Button refreshHistory = new Theme.Button("Làm mới", "plain");
        historyNav.add(historyPages); historyNav.add(historyPrevious); historyNav.add(historyNext); historyNav.add(refreshHistory);
        historyPanel.add(historyNav, BorderLayout.SOUTH);
        tabs.addTab("Yêu cầu", requestPanel); tabs.addTab("Đã cho / Đã nhận", historyPanel);
        Theme.tabs(tabs);
        root.add(tabs, BorderLayout.CENTER);
        JPanel footer = Theme.transparent(new BorderLayout(10, 0));
        footer.add(status, BorderLayout.CENTER); footer.add(retry, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(1050, 570);
        setMinimumSize(new Dimension(850, 440));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        requestTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        requestTable.getSelectionModel().addListSelectionListener(e -> buttons());
        accept.addActionListener(e -> acceptSelected());
        decline.addActionListener(e -> selectedReply(PointRequestDecision.DECLINE));
        cancel.addActionListener(e -> selectedReply(PointRequestDecision.CANCEL));
        block.addActionListener(e -> { PointRequestItem selected = selected(); if (selected != null) blockAction.accept(selected.requesterId()); });
        retry.addActionListener(e -> retryAction.run());
        requestPrevious.addActionListener(e -> requestLoader.accept(requestPage - 1));
        requestNext.addActionListener(e -> requestLoader.accept(requestPage + 1));
        historyPrevious.addActionListener(e -> historyLoader.accept(historyPage - 1));
        historyNext.addActionListener(e -> historyLoader.accept(historyPage + 1));
        refreshRequests.addActionListener(e -> requestLoader.accept(requestPage));
        refreshHistory.addActionListener(e -> historyLoader.accept(historyPage));
        refreshRequests.setToolTipText("Yêu cầu làm mới được xếp sau thao tác đang chờ.");
        refreshHistory.setToolTipText("Yêu cầu làm mới được xếp sau thao tác đang chờ.");
        tabs.addChangeListener(e -> { if (tabs.getSelectedIndex() == 1) historyLoader.accept(historyPage); });
        addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent e) { clock.stop(); } });
        retry.setVisible(false);
        clock.start();
        buttons();
    }

    private static DefaultTableModel rows(String... columns) {
        return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int row, int col) { return false; } };
    }

    private static JScrollPane scroll(PlainTable table) {
        Theme.table(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        scroll.getViewport().setBackground(Theme.PANEL); return scroll;
    }

    private static void columns(PlainTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    }

    public void onReply(BiConsumer<PointRequestItem, PointRequestDecision> action) { reply = action; }
    public void onBlock(LongConsumer action) { blockAction = action; }
    public void onRequestLoad(IntConsumer action) { requestLoader = action; }
    public void onHistoryLoad(IntConsumer action) { historyLoader = action; }
    public void onRetry(Runnable action) { retryAction = action; }

    public void context(long id, RoomSnapshot snapshot, java.math.BigDecimal currentBalance, Set<Long> ignoredUsers) {
        userId = id; room = snapshot; balance = currentBalance; ignored = Set.copyOf(ignoredUsers); updateClock();
    }

    public void busy(boolean value, boolean unresolved) {
        busy = value; retry.setVisible(unresolved); retry.setEnabled(!value); buttons();
    }

    public void message(String text) { status.setText(text); status.setToolTipText(text); }

    public void requests(PageResult<PointRequestItem> result) {
        requestTable.emptyText("Chưa có yêu cầu điểm. Bạn có thể gửi từ bàn chờ.");
        String selectedId = selected() == null ? null : selected().requestId();
        requestPage = result.page(); requestTotalPages = result.totalPages(); requestTotalItems = result.totalItems(); pageItems = List.copyOf(result.items());
        requests.setRowCount(0);
        for (PointRequestItem item : pageItems) {
            boolean incoming = item.targetUserId() == userId;
            requests.addRow(new Object[]{TIME.format(Instant.ofEpochMilli(item.createdAtMillis())), incoming ? "Nhận yêu cầu" : "Đã gửi",
                    incoming ? item.requesterName() + " (#" + item.requesterId() + ")" : item.targetName() + " (#" + item.targetUserId() + ")",
                    Theme.points(item.amount()), item.message(), "", ""});
        }
        for (int i = 0; i < pageItems.size(); i++) if (pageItems.get(i).requestId().equals(selectedId)) requestTable.setRowSelectionInterval(i, i);
        requestPages.setText(pageText(result));
        status.setText("Chọn một yêu cầu để cho điểm, từ chối hoặc hủy.");
        updateClock();
    }

    public void updateRequest(PointRequestItem item) {
        java.util.ArrayList<PointRequestItem> next = new java.util.ArrayList<>(pageItems);
        int found = -1;
        for (int i = 0; i < next.size(); i++) if (next.get(i).requestId().equals(item.requestId())) { found = i; break; }
        if (found >= 0) next.set(found, item);
        else if (requestPage == 0) { next.addFirst(item); requestTotalItems++; if (next.size() > 20) next.removeLast(); }
        else return;
        requests(new PageResult<>(next, requestPage, 20, Math.max(next.size(), requestTotalItems), PageResult.calcTotalPages(Math.max(next.size(), requestTotalItems), 20)));
    }

    public void history(PageResult<PointTransferReceipt> result) {
        historyPage = result.page(); historyTotalPages = result.totalPages(); history.setRowCount(0);
        for (PointTransferReceipt item : result.items()) {
            history.addRow(new Object[]{TIME.format(Instant.ofEpochMilli(item.committedAtMillis())),
                    item.senderId() == userId ? "Đã cho" : "Đã nhận", item.senderName() + " (#" + item.senderId() + ")",
                    item.recipientName() + " (#" + item.recipientId() + ")", Theme.points(item.amount()), item.message(), item.transferId()});
        }
        historyPages.setText(pageText(result)); buttons();
    }

    private static String pageText(PageResult<?> page) {
        return page.totalItems() == 0 ? "Chưa có dữ liệu" : (page.page() + 1) + " / " + page.totalPages() + " · " + page.totalItems() + " mục";
    }

    private PointRequestItem selected() {
        int row = requestTable.getSelectedRow();
        return row >= 0 && row < pageItems.size() ? pageItems.get(row) : null;
    }

    private void selectedReply(PointRequestDecision decision) {
        PointRequestItem item = selected(); if (item != null && !busy) reply.accept(item, decision);
    }

    private void acceptSelected() {
        PointRequestItem item = selected();
        if (item == null || !PointUiRules.canAccept(item, userId, room, System.currentTimeMillis(), ignored.contains(item.requesterId()))) return;
        if (balance.compareTo(item.amount()) < 0) { message("Số dư của bạn không đủ để cho điểm."); return; }
        JTextArea text = new JTextArea(PointUiRules.confirmation(item.requesterName(), item.requesterId(), item.amount(), balance));
        text.setEditable(false); text.setOpaque(false); text.setForeground(Theme.TEXT); text.setFont(Theme.font(14, false));
        if (JOptionPane.showConfirmDialog(this, text, "Xác nhận cho điểm", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION) {
            selectedReply(PointRequestDecision.ACCEPT);
        }
    }

    private void updateClock() {
        long now = System.currentTimeMillis();
        for (int row = 0; row < pageItems.size(); row++) {
            PointRequestItem item = pageItems.get(row);
            String state = PointUiRules.status(item, now);
            if (item.targetUserId() == userId && ignored.contains(item.requesterId())) state += " · Đã bỏ qua";
            requests.setValueAt(state, row, 5);
            requests.setValueAt(PointUiRules.pending(item, now) ? Math.max(1, (item.expiresAtMillis() - now + 999) / 1000) + "s" : "—", row, 6);
        }
        buttons();
    }

    private void buttons() {
        PointRequestItem item = selected();
        boolean pending = PointUiRules.pending(item, System.currentTimeMillis());
        accept.setEnabled(!busy && PointUiRules.canAccept(item, userId, room, System.currentTimeMillis(), item != null && ignored.contains(item.requesterId())));
        decline.setEnabled(!busy && pending && item.targetUserId() == userId);
        cancel.setEnabled(!busy && pending && item.requesterId() == userId);
        block.setEnabled(!busy && item != null && item.targetUserId() == userId);
        block.setText(item != null && ignored.contains(item.requesterId()) ? "Nhận lại yêu cầu" : "Bỏ qua người này");
        accept.setToolTipText("Chỉ cho điểm khi hai người đang cùng bàn chờ và yêu cầu chưa hết hạn.");
        requestPrevious.setEnabled(!busy && requestPage > 0); requestNext.setEnabled(!busy && requestPage + 1 < requestTotalPages);
        historyPrevious.setEnabled(!busy && historyPage > 0); historyNext.setEnabled(!busy && historyPage + 1 < historyTotalPages);
    }
}
