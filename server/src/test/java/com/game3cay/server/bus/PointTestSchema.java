package com.game3cay.server.bus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

/** Uses the product's actual table definitions; strips only MySQL engine/index syntax for H2. */
public final class PointTestSchema {
    private PointTestSchema() {}
    public static void create(Connection connection) throws Exception {
        String sql = Files.readString(Path.of("../database/setup.sql"));
        sql = sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS point_requests"),
                sql.indexOf("INSERT IGNORE INTO app_migrations(name) VALUES ('schema_v3_point_transfers')"));
        if (connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")) {
            sql = sql.replaceAll("(?m)^\\s*INDEX [^\\r\\n]*\\R", "")
                    .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        }
        for (String command : sql.split(";")) {
            if (!command.isBlank()) try (var statement = connection.createStatement()) { statement.execute(command); }
        }
    }
}
