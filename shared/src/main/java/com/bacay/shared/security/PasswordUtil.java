package com.bacay.shared.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Tiện ích hash và xác minh mật khẩu bằng PBKDF2WithHmacSHA256.
 *
 * <p>Cài đặt mặc định (cấu hình được qua constructor):
 * <ul>
 *   <li>Thuật toán: PBKDF2WithHmacSHA256</li>
 *   <li>Số vòng lặp: 310 000 (OWASP 2023)</li>
 *   <li>Độ dài key: 256 bit</li>
 *   <li>Salt: 16 byte ngẫu nhiên</li>
 * </ul>
 *
 * <p>Định dạng lưu vào DB (cột {@code password_hash}):
 * <pre>{@code algorithm$iterations$saltBase64$hashBase64}</pre>
 *
 * <p>Payload trả về client KHÔNG ĐƯỢC chứa hash hoặc salt.
 */
public final class PasswordUtil {

    private static final String ALGORITHM  = "PBKDF2WithHmacSHA256";
    private static final int    ITERATIONS  = 310_000;
    private static final int    KEY_LENGTH  = 256; // bits
    private static final int    SALT_BYTES  = 16;

    private PasswordUtil() {}

    /**
     * Tạo hash từ mật khẩu thô.
     *
     * @param rawPassword mật khẩu người dùng nhập (không lưu lại)
     * @return chuỗi lưu DB dạng {@code algorithm$iterations$saltBase64$hashBase64}
     */
    public static String hash(String rawPassword) {
        byte[] salt = generateSalt();
        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        return ALGORITHM + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Xác minh mật khẩu với hash đã lưu.
     *
     * @param rawPassword mật khẩu cần kiểm tra
     * @param storedHash  chuỗi lấy từ DB
     * @return {@code true} nếu khớp
     */
    public static boolean verify(String rawPassword, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt    = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual   = pbkdf2(rawPassword.toCharArray(), salt, iterations, expected.length * 8);
            return slowEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    // --- Helpers ---

    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] result = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return result;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 không khả dụng", e);
        }
    }

    /** So sánh constant-time để tránh timing attack. */
    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
