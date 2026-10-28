package com.game3cay.server.dal;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionProvider extends AutoCloseable {
    Connection open() throws SQLException;

    @Override
    default void close() {}
}
