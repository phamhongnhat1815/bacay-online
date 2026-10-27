package com.game3cay.shared.network;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiện ích đọc cấu hình ứng dụng từ file properties và biến môi trường / System properties.
 * <p>
 * Thứ tự ưu tiên:
 * 1. System property (-Dkey=value)
 * 2. Cấu hình trong resource properties
 * 3. Giá trị mặc định (fallback)
 */
public final class RuntimeSettings {

    private static final ConcurrentHashMap<String, Properties> CACHED_PROPERTIES = new ConcurrentHashMap<>();

    private RuntimeSettings() {}

    /**
     * Đọc giá trị chuỗi cấu hình.
     *
     * @param resource tên file properties trong classpath (ví dụ: "server.properties")
     * @param key      tên khóa cấu hình
     * @param fallback giá trị mặc định nếu không tìm thấy
     * @return giá trị cấu hình tương ứng
     */
    public static String text(String resource, String key, String fallback) {
        Properties properties = CACHED_PROPERTIES.computeIfAbsent(resource, RuntimeSettings::loadProperties);
        return System.getProperty(key, properties.getProperty(key, fallback));
    }

    /**
     * Đọc giá trị số nguyên cấu hình trong khoảng [min, max].
     *
     * @param resource tên file properties
     * @param key      tên khóa cấu hình
     * @param fallback giá trị mặc định
     * @param min      giá trị nhỏ nhất cho phép
     * @param max      giá trị lớn nhất cho phép
     * @return giá trị số nguyên hợp lệ
     * @throws IllegalArgumentException nếu giá trị không phải số hoặc nằm ngoài khoảng [min, max]
     */
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
