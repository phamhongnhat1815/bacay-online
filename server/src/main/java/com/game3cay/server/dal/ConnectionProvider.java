package com.game3cay.server.dal;

import java.sql.Connection;
import java.sql.SQLException;

/** Mỗi lần gọi phải trả một connection riêng; bên gọi chịu trách nhiệm đóng. */
@FunctionalInterface
public interface ConnectionProvider {
  Connection open() throws SQLException;
}
