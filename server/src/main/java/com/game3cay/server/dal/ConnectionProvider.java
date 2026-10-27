package com.game3cay.server.dal;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional Interface cung cấp kết nối cơ sở dữ liệu.
 * <p>
 * Mỗi lần gọi {@link #open()} phải trả về một {@link Connection} mới;
 * bên gọi có trách nhiệm giải phóng/đóng kết nối (khuyến nghị dùng try-with-resources).
 */
@FunctionalInterface
public interface ConnectionProvider {

    /**
     * Mở một kết nối mới tới cơ sở dữ liệu.
     *
     * @return {@link Connection} đang mở
     * @throws SQLException nếu xảy ra lỗi kết nối
     */
    Connection open() throws SQLException;
}
