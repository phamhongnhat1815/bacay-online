package com.game3cay.gui;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

public final class PlainTable extends JTable {
    public PlainTable(TableModel model) {
        super(model);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        setDefaultRenderer(Object.class, renderer);
    }
}
