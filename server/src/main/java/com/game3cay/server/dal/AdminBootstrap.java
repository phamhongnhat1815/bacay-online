package com.game3cay.server.dal;

import com.game3cay.shared.security.PasswordUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

/** Creates an installation-specific administrator before the TCP listener starts. */
public final class AdminBootstrap {
    private AdminBootstrap() {}

    public static boolean initialize(ConnectionProvider connections, Path credentialFile)
            throws SQLException, IOException {
        return initialize(connections, credentialFile, System.getenv("BACAY_ADMIN_PASSWORD"));
    }

    public static boolean initialize(ConnectionProvider connections, Path credentialFile, String configuredPassword)
            throws SQLException, IOException {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                String existingHash = null;
                String existingRole = null;
                try (var query = connection.prepareStatement(
                        "SELECT password_hash, role FROM users WHERE username = 'admin' FOR UPDATE");
                     var rows = query.executeQuery()) {
                    if (rows.next()) {
                        existingHash = rows.getString(1);
                        existingRole = rows.getString(2);
                    }
                }
                if (existingHash != null && (!"ADMIN".equalsIgnoreCase(existingRole)
                        || !PasswordUtil.verify("admin@123", existingHash))) {
                    // Never promote an existing player or overwrite a customized administrator.
                    connection.rollback();
                    if (!"ADMIN".equalsIgnoreCase(existingRole)) {
                        throw new SQLException("Tên admin đang thuộc tài khoản không có quyền ADMIN; cần đổi tên trước khi bootstrap");
                    }
                    return false;
                }

                String password = bootstrapPassword(credentialFile, configuredPassword);
                String hash = PasswordUtil.hash(password);
                if (existingHash == null) {
                    try (var insert = connection.prepareStatement(
                            "INSERT INTO users (username,password_hash,display_name,balance,status,role) "
                                    + "VALUES ('admin',?,'Quản Trị Viên',0.00,'ACTIVE','ADMIN')")) {
                        insert.setString(1, hash);
                        insert.executeUpdate();
                    }
                } else {
                    try (var update = connection.prepareStatement(
                            "UPDATE users SET password_hash=? WHERE username='admin' AND password_hash=?")) {
                        update.setString(1, hash);
                        update.setString(2, existingHash);
                        if (update.executeUpdate() != 1) throw new SQLException("Tài khoản admin vừa được thay đổi");
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | IOException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }

    private static String bootstrapPassword(Path credentialFile, String configured) throws IOException {
        Path file = credentialFile.toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        if (Files.isSymbolicLink(file)) throw new IOException("Tệp bootstrap không được là symbolic link");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            restrictToOwner(file);
            Properties saved = new Properties();
            try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { saved.load(input); }
            String password = saved.getProperty("password");
            validatePassword(password);
            if (configured != null && !configured.isBlank() && !configured.equals(password)) {
                throw new IOException("Tệp bootstrap đã tồn tại với mật khẩu khác BACAY_ADMIN_PASSWORD; kiểm tra tệp trước khi thử lại");
            }
            return password;
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String password = configured == null || configured.isBlank()
                ? Base64.getUrlEncoder().withoutPadding().encodeToString(random) : configured;
        validatePassword(password);
        try {
            if (Files.getFileAttributeView(file.getParent(), java.nio.file.attribute.PosixFileAttributeView.class) != null) {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createFile(file);
            }
            restrictToOwner(file);
            // File is durable before DB commit: a failed transaction can safely reuse the same credential.
            Files.writeString(file, "username=admin\npassword=" + escape(password) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
        } catch (IOException e) {
            throw new IOException("Không thể lưu thông tin bootstrap admin an toàn: " + file, e);
        }
        return password;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void validatePassword(String value) throws IOException {
        if (value == null || value.length() < 16 || value.length() > 128 || value.isBlank() || !value.equals(value.strip())
                || "admin@123".equals(value) || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IOException("Mật khẩu bootstrap cần từ 16 đến 128 ký tự và không được dùng mật khẩu mặc định cũ");
        }
    }

    private static void restrictToOwner(Path file) throws IOException {
        var posix = Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) throw new IOException("Hệ thống tệp không hỗ trợ giới hạn quyền tệp bootstrap");
        var owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
        acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
}
