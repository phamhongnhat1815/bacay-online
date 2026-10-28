package com.game3cay.admin.gui;

import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.PageResult;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminUiRegressionTest {
    @Test void untrustedLabelsAndTableCellsStayPlainText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String markup = "<html><img src='https://invalid.example/image'>Name";
            var label = AdminTheme.label(markup, 12, AdminTheme.TEXT);
            assertEquals(Boolean.TRUE, label.getClientProperty("html.disable"));
            assertNull(label.getClientProperty(BasicHTML.propertyKey));
            for (Container panel : List.of(new UsersPanel(), new RoomsPanel(), new LedgerPanel())) {
                JTable table = findTable(panel);
                assertNotNull(table);
                table.setModel(new javax.swing.table.DefaultTableModel(new Object[][] {{markup}}, new Object[] {"Name"}));
                Component cell = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
                assertEquals(Boolean.TRUE, ((JComponent) cell).getClientProperty("html.disable"));
                assertNull(((JComponent) cell).getClientProperty(BasicHTML.propertyKey));
            }
        });
    }

    @Test void ledgerDisplaysAdjustmentActorAndReason() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LedgerPanel panel = new LedgerPanel();
            panel.updatePage(new PageResult<>(List.of(new AdminLedgerItem("ADJ-1", 9, "player", BigDecimal.TEN,
                    BigDecimal.ZERO, BigDecimal.TEN, 1, "ADMIN_ADJUSTMENT", "admin", "Refund")), 0, 20, 1, 1));
            JTable table = findTable(panel);
            assertEquals(1, table.getRowCount());
            assertEquals("ADMIN_ADJUSTMENT", table.getValueAt(0, 7));
            assertEquals("admin", table.getValueAt(0, 8));
            assertEquals("Refund", table.getValueAt(0, 9));
        });
    }

    private static JTable findTable(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTable table) return table;
            if (child instanceof Container nested) {
                JTable table = findTable(nested);
                if (table != null) return table;
            }
        }
        return null;
    }
}
