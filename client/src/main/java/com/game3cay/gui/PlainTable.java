package com.game3cay.gui;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import java.awt.*;

public final class PlainTable extends JTable {
    private String emptyText = "Chưa có dữ liệu";
    public PlainTable(TableModel model) {
        super(model);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        setDefaultRenderer(Object.class, renderer);
    }

    public void emptyText(String text) { emptyText = text; repaint(); }

    @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        if (component instanceof JComponent cell) {
            Object value = getValueAt(row, column);
            cell.setToolTipText(value == null ? null : value.toString());
        }
        return component;
    }

    @Override public JToolTip createToolTip() {
        JToolTip tip = super.createToolTip();
        tip.putClientProperty("html.disable", Boolean.TRUE);
        return tip;
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (getRowCount() == 0) {
            Graphics2D g = (Graphics2D) graphics.create();
            Assets.quality(g);
            g.setFont(Theme.font(14, false));
            g.setColor(Theme.MUTED);
            g.drawString(emptyText, Math.max(16, (getWidth() - g.getFontMetrics().stringWidth(emptyText)) / 2),
                    Math.max(45, Math.min(110, getHeight() / 2)));
            g.dispose();
        }
    }
}
