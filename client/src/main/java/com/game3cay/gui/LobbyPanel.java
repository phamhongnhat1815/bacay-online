package com.game3cay.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import com.game3cay.shared.model.RoomSummary;

public final class LobbyPanel extends JPanel {
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{
                "ID", "Mã", "Tên phòng", "Trạng thái",
                "Người", "Điểm cơ bản"
            }, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Long.class
                    : column == 5 ? java.math.BigDecimal.class
                    : String.class;
        }
    };

    private final JTable table = new JTable(model);
    private List<RoomSummary> rooms = List.of();

    private final JLabel count =
            new JLabel("Chưa tải danh sách phòng.");

    private final JTextField name = new JTextField(20);
    private final JSpinner min =
            new JSpinner(new SpinnerNumberModel(2, 2, 8, 1));
    private final JSpinner max =
            new JSpinner(new SpinnerNumberModel(8, 2, 8, 1));
    private final JTextField points = new JTextField(10);

    private final JButton refresh = new JButton("Tải danh sách");
    private final JButton create = new JButton("Tạo phòng");
    private final JButton join = new JButton("Vào phòng đã chọn");

    public LobbyPanel() {
        super(new BorderLayout(8, 8));

        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refresh);
        top.add(join);
        top.add(count);
        add(top, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createTitledBorder("Tạo phòng"));

        form.add(new JLabel("Tên phòng:"));
        form.add(name);
        form.add(new JLabel("Số người tối thiểu:"));
        form.add(min);
        form.add(new JLabel("Số người tối đa:"));
        form.add(max);
        form.add(new JLabel("Điểm cơ bản:"));
        form.add(points);
        form.add(new JLabel(""));
        form.add(create);

        add(form, BorderLayout.SOUTH);
        setActionsEnabled(false);
    }

    public void onRefresh(Runnable action) {
        refresh.addActionListener(e -> action.run());
    }

    public void onCreate(Runnable action) {
        create.addActionListener(e -> action.run());
    }

    public void onJoin(Runnable action) {
        join.addActionListener(e -> action.run());
    }

    public String roomName() {
        return name.getText().trim();
    }

    public int minPlayers() {
        return spinnerValue(min);
    }

    public int maxPlayers() {
        return spinnerValue(max);
    }

    public String pointText() {
        return points.getText().trim();
    }

    private static int spinnerValue(JSpinner spinner) {
        try {
            spinner.commitEdit();
            return (Integer) spinner.getValue();
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(
                    "Số người phải là số nguyên.", e
            );
        }
    }

    public long selectedRoomId() {
        int selected = table.getSelectedRow();

        return selected < 0 ? -1
                : rooms.get(
                        table.convertRowIndexToModel(selected)
                ).roomId();
    }

    public void setRooms(List<RoomSummary> values) {
        long selected = selectedRoomId();
        rooms = List.copyOf(values);
        model.setRowCount(0);

        for (RoomSummary r : rooms) {
            model.addRow(new Object[]{
                r.roomId(),
                r.roomCode(),
                r.roomName(),
                r.status(),
                r.playerCount() + "/" + r.maxPlayers(),
                r.betAmount()
            });
        }

        count.setText(
                rooms.isEmpty() ? "Chưa có phòng."
                        : rooms.size() + " phòng"
        );

        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).roomId() == selected) {
                int row = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(row, row);
                break;
            }
        }
    }

    public void setActionsEnabled(boolean enabled) {
        for (JComponent c : new JComponent[]{
                table, name, min, max, points, refresh, create, join
        }) {
            c.setEnabled(enabled);
        }
    }
}