package com.game3cay.admin.gui;

import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.PageResult;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JButton;
import javax.swing.JTextField;
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

    @Test void pressingEnterCannotBroadcastAgainWhileTheFirstRequestIsPending() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DashboardPanel panel = new DashboardPanel();
            var sent = new java.util.concurrent.atomic.AtomicInteger();
            panel.setOnBroadcast(request -> sent.incrementAndGet());
            JTextField message = findComponent(panel, JTextField.class);
            message.setText("Thông báo thử nghiệm");
            message.postActionEvent();
            message.postActionEvent();
            assertEquals(1, sent.get(), "Enter must respect the disabled send button");
            panel.broadcastFailed("Mất kết nối");
            message.postActionEvent();
            assertEquals(2, sent.get(), "A completed failure must let the admin retry");
            panel.reset();
            assertEquals("", message.getText());
            message.setText("Thông báo của phiên mới");
            message.postActionEvent();
            assertEquals(3, sent.get(), "A new login must not inherit a disabled send button");
        });
    }

    @Test void loginEnterCannotStartAnotherSessionWhileConnecting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AdminAuthPanel panel = new AdminAuthPanel();
            var attempts = new java.util.concurrent.atomic.AtomicInteger();
            panel.setOnLogin(credentials -> {
                attempts.incrementAndGet();
                java.util.Arrays.fill(credentials.password(), '\0');
            });
            var password = findComponent(panel, javax.swing.JPasswordField.class);
            password.setText("password123");
            password.postActionEvent();
            password.postActionEvent();
            assertEquals(1, attempts.get());
            panel.showError("Cho phép thử lại");
            password.setText("password123");
            password.postActionEvent();
            assertEquals(2, attempts.get());
        });
    }

    @Test void oversizedBroadcastIsRejectedBeforeItStartsARequest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DashboardPanel panel = new DashboardPanel();
            var sent = new java.util.concurrent.atomic.AtomicInteger();
            panel.setOnBroadcast(request -> sent.incrementAndGet());
            var message = findComponent(panel, JTextField.class);
            message.setText("x".repeat(501));
            message.postActionEvent();
            assertEquals(0, sent.get());
            assertTrue(findButtons(panel).stream().allMatch(JButton::isEnabled));
            message.setText("x".repeat(500));
            message.postActionEvent();
            assertEquals(1, sent.get());
        });
    }

    @Test void resettingUsersClearsOldSessionDataWithoutIssuingASearch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UsersPanel panel = new UsersPanel();
            var searches = new java.util.concurrent.atomic.AtomicInteger();
            panel.setOnSearch(request -> searches.incrementAndGet());
            findComponent(panel, JTextField.class).setText("old account");
            findComponent(panel, javax.swing.JComboBox.class).setSelectedItem("BLOCKED");
            panel.setActionsBusy(true);
            int beforeReset = searches.get();
            panel.reset();
            assertEquals(beforeReset, searches.get(), "Reset must not queue requests for a closed session");
            assertEquals("", findComponent(panel, JTextField.class).getText());
            assertEquals("Tất cả", findComponent(panel, javax.swing.JComboBox.class).getSelectedItem());
            assertEquals(0, findTable(panel).getRowCount());
            assertEquals(0, panel.getCurrentPage());
            assertTrue(findButtons(panel).stream().filter(button -> button.getText().equals("CỘNG / TRỪ ĐIỂM")
                    || button.getText().equals("KHÓA / MỞ KHÓA")).allMatch(JButton::isEnabled));
        });
    }

    private static List<JButton> findButtons(Container root) {
        java.util.ArrayList<JButton> result = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button) result.add(button);
            if (child instanceof Container nested) result.addAll(findButtons(nested));
        }
        return result;
    }

    private static <T extends Component> T findComponent(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T result = findComponent(nested, type);
                if (result != null) return result;
            }
        }
        return null;
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
