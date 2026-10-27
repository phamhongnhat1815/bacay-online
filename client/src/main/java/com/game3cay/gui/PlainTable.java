package com.game3cay.gui;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

/**
 * Bảng hiển thị dữ liệu {@link JTable} an toàn.
 * <p>
 * Mọi ô dữ liệu hiển thị (tên người dùng, tin nhắn chat, kết quả ván) đều được vô hiệu hóa
 * việc biên dịch mã HTML nhằm triệt tiêu nguy cơ HTML/Swing injection.
 */
public final class PlainTable extends JTable {

    public PlainTable(TableModel model) {
        super(model);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        setDefaultRenderer(Object.class, renderer);
    }
}
