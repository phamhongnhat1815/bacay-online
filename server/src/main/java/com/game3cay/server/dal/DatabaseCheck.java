package com.game3cay.server.dal;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Kiểm tra đọc DB thật trước demo M2–M3; không sửa dữ liệu hay in config nhạy cảm. */
public final class DatabaseCheck {
    private DatabaseCheck() {}

    public static void main(String[] args) {
        try (Connection connection = DatabaseConfig.fromEnvironment().open();
             Statement statement = connection.createStatement()) {
            // Kiểm tra các cột mà UserDAO cần, kể cả khi bảng users chưa có dữ liệu.
            try (ResultSet ignored = statement.executeQuery(
                    "SELECT id,username,password_hash,display_name,email,avatar_url,balance,status,"
                    + "total_games,total_wins,total_losses FROM users WHERE 1=0")) {
                System.out.println("OK: kết nối MySQL và cấu trúc users cho M2–M3");
            }
            try (ResultSet cards = statement.executeQuery(
                    "SELECT COUNT(*),COUNT(DISTINCT suit),COUNT(DISTINCT `rank`) FROM cards")) {
                cards.next();
                if (cards.getInt(1) != 52 || cards.getInt(2) != 4 || cards.getInt(3) != 13) {
                    throw new IllegalStateException("Bộ bài chưa đủ 52 lá, 4 chất, 13 hạng");
                }
                System.out.println("OK: seed bộ bài 52 lá");
            }
        } catch (Exception e) {
            System.err.println("Kiểm tra DB thất bại (" + e.getClass().getSimpleName()
                    + "). Kiểm tra biến môi trường, quyền SELECT và schema; không in mật khẩu/hash.");
            System.exit(1);
        }
    }
}
