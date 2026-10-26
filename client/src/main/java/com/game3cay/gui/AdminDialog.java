package com.game3cay.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

import com.game3cay.shared.model.AdminPayload;

public final class AdminDialog extends JDialog {
  private final DefaultTableModel rows =
      new DefaultTableModel(
          new Object[]{"ID", "Tài khoản", "Tên", "Quyền", "Trạng thái", "Điểm"},
          0) {
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };

  private final JTable table = new JTable(rows);
  private final JTextField search = new JTextField(15);
  private final JTextField amount = new JTextField("1000", 9);
  private final JLabel status = new JLabel(" ");
  private final JPanel cards = new JPanel();
  private final List<JButton> buttons = new ArrayList<>();

  private Consumer<AdminPayload.Request> sender;
  private List<AdminPayload.Account> accounts = List.of();
  private int page, totalPages = 1;
  private boolean busy;

  public AdminDialog(JFrame owner) {
    super(owner, "Quản trị", false);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    var tabs = new JTabbedPane();
    var users = new JPanel(new BorderLayout(10, 10));
    users.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    var top = new JPanel(new FlowLayout(FlowLayout.LEFT));
    top.add(new JLabel("Tìm tài khoản / tên:"));
    top.add(search);

    button(top, "Tìm / Tải lại", () -> list(0));
    button(top, "Trước", () -> list(Math.max(0, page - 1)));
    button(top, "Sau", () -> list(Math.min(totalPages - 1, page + 1)));

    users.add(top, BorderLayout.NORTH);
    table.setRowHeight(30);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    users.add(new JScrollPane(table), BorderLayout.CENTER);

    var bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
    bottom.add(new JLabel("Điểm cấp:"));
    bottom.add(amount);

    button(bottom, "Cấp điểm", this::credit);
    button(bottom, "Khóa / Mở khóa", this::block);
    button(bottom, "Đặt lại mật khẩu", this::resetPassword);

    users.add(bottom, BorderLayout.SOUTH);
    tabs.addTab("Tài khoản", users);

    cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
    tabs.addTab("Bài trên bàn đang xem", new JScrollPane(cards));

    var root = new JPanel(new BorderLayout());
    root.add(tabs, BorderLayout.CENTER);
    root.add(status, BorderLayout.SOUTH);

    setContentPane(root);
    setSize(1050, 700);
    setLocationRelativeTo(owner);

    clearCards();
    search.addActionListener(e -> list(0));
  }

  private void button(JPanel panel, String title, Runnable action) {
    var b = new JButton(title);
    b.addActionListener(e -> action.run());
    panel.add(b);
    buttons.add(b);
  }

  public void onRequest(Consumer<AdminPayload.Request> action) {
    sender = action;
  }

  public void load() {
    list(0);
  }

  private void list(int p) {
    send(new AdminPayload.Request(
        AdminPayload.Action.LIST_USERS,
        0, null, search.getText(), p));
  }

  private void send(AdminPayload.Request request) {
    if (!busy && sender != null) {
      sender.accept(request);
    }
  }

  private AdminPayload.Account selected() {
    int row = table.getSelectedRow();

    if (row < 0 || row >= accounts.size()) {
      failure("Chọn một tài khoản trong bảng trước.");
      return null;
    }

    return accounts.get(table.convertRowIndexToModel(row));
  }

  private void credit() {
    var target = selected();
    if (target == null) {
      return;
    }

    try {
      var n = new BigDecimal(amount.getText().trim());

      if (n.signum() <= 0
          || n.scale() < 0
          || n.scale() > 2
          || n.precision() > 15) {
        throw new NumberFormatException();
      }

      if (JOptionPane.showConfirmDialog(
              this,
              "Cấp " + Theme.points(n)
                  + " điểm cho " + target.username() + "?",
              "Cấp điểm",
              JOptionPane.OK_CANCEL_OPTION)
          == JOptionPane.OK_OPTION) {
        send(new AdminPayload.Request(
            AdminPayload.Action.CREDIT,
            target.userId(), n, null, page));
      }
    } catch (NumberFormatException e) {
      failure("Nhập điểm dương, tối đa 2 số thập phân.");
    }
  }

  private void block() {
    var target = selected();
    if (target == null) {
      return;
    }

    String next =
        "BLOCKED".equals(target.status()) ? "ACTIVE" : "BLOCKED";

    if (JOptionPane.showConfirmDialog(
            this,
            ("BLOCKED".equals(next) ? "Khóa " : "Mở khóa ")
                + target.username() + "?",
            "Trạng thái",
            JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION) {
      send(new AdminPayload.Request(
          AdminPayload.Action.SET_BLOCKED,
          target.userId(), null, next, page));
    }
  }

  private void resetPassword() {
    var target = selected();
    if (target == null) {
      return;
    }

    var field = new JPasswordField();

    if (JOptionPane.showConfirmDialog(
            this, field,
            "Mật khẩu mới cho " + target.username(),
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) {
      return;
    }

    char[] value = field.getPassword();

    try {
      if (value.length < 8 || value.length > 128) {
        failure("Mật khẩu cần 8–128 ký tự.");
        return;
      }

      send(new AdminPayload.Request(
          AdminPayload.Action.RESET_PASSWORD,
          target.userId(), null, new String(value), page));
    } finally {
      Arrays.fill(value, '\0');
      field.setText("");
    }
  }

  public void setBusy(boolean value) {
    busy = value;
    buttons.forEach(b -> b.setEnabled(!value));
    search.setEnabled(!value);
    amount.setEnabled(!value);
  }

  public void showResponse(AdminPayload.Response response) {
    accounts = response.accounts();
    page = response.page();
    totalPages = response.totalPages();

    rows.setRowCount(0);
    for (var a : accounts) {
      rows.addRow(new Object[]{
          a.userId(), a.username(), a.displayName(),
          a.role(), a.status(), Theme.points(a.balance())
      });
    }

    status.setText(
        response.message() + " | Trang "
            + (page + 1) + "/" + totalPages);
  }

  public void failure(String text) {
    status.setText(text);
  }

  public void clearCards() {
    cards.removeAll();
    cards.add(new JLabel(
        "Chọn Xem bàn ở sảnh để kiểm tra bài của ván đang diễn ra."));
    cards.revalidate();
    cards.repaint();
  }

  public void showCards(AdminPayload.TableCards value) {
    cards.removeAll();
    cards.add(new JLabel(
        "Bàn " + value.roomId() + " | Ván " + value.roundId()));

    for (var p : value.players()) {
      var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
      row.add(new JLabel(
          p.displayName() + " (#" + p.userId() + ")"));

      for (var card : p.cards()) {
        row.add(new JLabel(
            card.toString(),
            new ImageIcon(
                Assets.card(card).getScaledInstance(
                    75, 105, Image.SCALE_SMOOTH)),
            SwingConstants.CENTER));
      }

      cards.add(row);
    }

    cards.revalidate();
    cards.repaint();
  }
}