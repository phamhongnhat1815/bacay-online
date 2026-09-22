package com.game3cay.server.Repository;

import com.game3cay.server.Model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByUsername(String username) {

        String sql = """
                SELECT
                    user_id,
                    username,
                    password_hash,
                    display_name,
                    email,
                    avatar_url,
                    balance,
                    status,
                    created_at,
                    updated_at
                FROM users
                WHERE username = ?
                """;

        List<User> users = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapRow(rs),
                username
        );

        return users.stream().findFirst();
    }

    public Optional<User> findByEmail(String email) {

        String sql = """
                SELECT
                    user_id,
                    username,
                    password_hash,
                    display_name,
                    email,
                    avatar_url,
                    balance,
                    status,
                    created_at,
                    updated_at
                FROM users
                WHERE email = ?
                """;

        List<User> users = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapRow(rs),
                email
        );

        return users.stream().findFirst();
    }

    public int insert(User user) {

        String sql = """
                INSERT INTO users (
                    username,
                    password_hash,
                    display_name,
                    email,
                    avatar_url,
                    balance,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        return jdbcTemplate.update(
                sql,
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getBalance(),
                user.getStatus()
        );
    }

    private User mapRow(
            java.sql.ResultSet rs
    ) throws java.sql.SQLException {

        User user = new User();

        user.setUserId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setDisplayName(rs.getString("display_name"));
        user.setEmail(rs.getString("email"));
        user.setAvatarUrl(rs.getString("avatar_url"));
        user.setBalance(rs.getBigDecimal("balance"));
        user.setStatus(rs.getString("status"));

        if (rs.getTimestamp("created_at") != null) {
            user.setCreatedAt(
                    rs.getTimestamp("created_at").toLocalDateTime()
            );
        }

        if (rs.getTimestamp("updated_at") != null) {
            user.setUpdatedAt(
                    rs.getTimestamp("updated_at").toLocalDateTime()
            );
        }

        return user;
    }
}