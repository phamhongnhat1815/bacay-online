package com.game3cay.gui;

import javax.swing.JTable;
import javax.swing.table.*;

/** Tên/chat lấy từ người dùng được hiển thị như văn bản. */
public final class PlainTable extends JTable {
  public PlainTable(TableModel model) {
    super(model);
    var renderer = new DefaultTableCellRenderer();
    renderer.putClientProperty("html.disable", Boolean.TRUE);
    setDefaultRenderer(Object.class, renderer);
  }
}
