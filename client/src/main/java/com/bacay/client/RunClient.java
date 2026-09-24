package com.bacay.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Entry point của client.
 *
 * <p>Khởi chạy trên EDT (Event Dispatch Thread) của Swing.
 * Kết nối mạng thực hiện trên luồng nền riêng — KHÔNG BẰNG GIỜ
 * thực hiện đọc/ghi socket trong sự kiện nút bấm hoặc EDT.
 *
 * <p>Chạy từ CLI:
 * <pre>{@code
 *   cd client
 *   mvn package -DskipTests
 *   java -jar target/bacay-client.jar
 *   # Override server:
 *   java -Dserver.host=192.168.1.10 -Dserver.port=8888 -jar target/bacay-client.jar
 * }</pre>
 */
public class RunClient {

    private static final Logger log = LoggerFactory.getLogger(RunClient.class);

    public static void main(String[] args) {
        Properties config = loadConfig();

        // Swing phải khởi động trên EDT
        SwingUtilities.invokeLater(() -> {
            try {
                // Dùng system look-and-feel cho native UI
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                log.warn("Không set được system L&F", e);
            }

            // TODO (Thành): new LoginFrame(config).setVisible(true);
            log.info("BaCay Client starting — server={}:{}",
                    config.getProperty("server.host", "localhost"),
                    config.getProperty("server.port", "8888"));

            // Placeholder: hiển thị dialog tạm thời
            JOptionPane.showMessageDialog(null,
                    "BaCay Client — Skeleton OK\nServer: "
                    + config.getProperty("server.host", "localhost") + ":"
                    + config.getProperty("server.port", "8888"),
                    "BaCay Online", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = RunClient.class.getResourceAsStream("/client.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            log.warn("Lỗi đọc client.properties — dùng mặc định", e);
        }
        props.putAll(System.getProperties());
        return props;
    }
}
