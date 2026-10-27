package com.game3cay.shared.network;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeSettings {
    private static final ConcurrentHashMap<String, Properties> CACHED_PROPERTIES = new ConcurrentHashMap<>();

    private RuntimeSettings() {}

    public static String text(String resource, String key, String fallback) {
        Properties properties = CACHED_PROPERTIES.computeIfAbsent(resource, RuntimeSettings::loadProperties);
        return System.getProperty(key, properties.getProperty(key, fallback));
    }

    public static int integer(String resource, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(text(resource, key, Integer.toString(fallback)));
            if (value < min || value > max) {
                throw new NumberFormatException("Giá trị nằm ngoài khoảng cho phép");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Khóa '" + key + "' phải là số nguyên từ " + min + " đến " + max, e);
        }
    }

    private static Properties loadProperties(String resourceName) {
        Properties properties = new Properties();
        try (InputStream inputStream = RuntimeSettings.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không thể đọc file cấu hình: " + resourceName, e);
        }
        return properties;
    }
}
