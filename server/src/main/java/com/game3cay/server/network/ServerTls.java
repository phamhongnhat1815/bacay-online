package com.game3cay.server.network;

import com.game3cay.shared.network.TlsTransport;
import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Provisions a private local identity; clients explicitly trust only the exported certificate. */
public final class ServerTls {
    private ServerTls() {}

    public static ServerSocket listen(int port) throws Exception {
        if (!TlsTransport.enabled()) return new ServerSocket(port);
        Path directory = Path.of(TlsTransport.setting("BACAY_TLS_DIR", "data/tls")).toAbsolutePath();
        Files.createDirectories(directory);
        restrict(directory);
        Path store = Path.of(TlsTransport.setting("BACAY_TLS_KEYSTORE", directory.resolve("server.p12").toString()));
        Path passwordFile = directory.resolve("keystore-password.txt");
        String password = TlsTransport.setting("BACAY_TLS_STORE_PASSWORD", "");
        if (password.isBlank()) {
            if (Files.exists(passwordFile)) {
                password = Files.readString(passwordFile, StandardCharsets.UTF_8).trim();
            } else {
                if (Files.exists(store)) throw new IOException("Thiếu mật khẩu keystore TLS hiện có");
                byte[] secret = new byte[36];
                new SecureRandom().nextBytes(secret);
                password = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
                Files.createFile(passwordFile);
                restrict(passwordFile);
                Files.writeString(passwordFile, password, StandardCharsets.UTF_8);
            }
        }
        if (!Files.exists(store)) generate(store, password);
        restrict(store);
        KeyStore identity = KeyStore.getInstance("PKCS12");
        char[] secret = password.toCharArray();
        try (var input = Files.newInputStream(store)) { identity.load(input, secret); }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(identity, secret);
        Arrays.fill(secret, '\0');
        var certificate = identity.getCertificate("bacay-server");
        if (certificate == null) throw new IOException("Keystore thiếu alias bacay-server");
        Path publicCertificate = directory.resolve("server.crt");
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
        Files.writeString(publicCertificate, pem, StandardCharsets.US_ASCII);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), null, null);
        SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(port);
        server.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        System.out.println("[TLS] Chứng chỉ công khai cho client: " + publicCertificate);
        return server;
    }

    private static void generate(Path target, String password) throws Exception {
        Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
        var command = List.of(keytool.toString(), "-genkeypair", "-alias", "bacay-server", "-keyalg", "RSA",
                "-keysize", "3072", "-sigalg", "SHA256withRSA", "-validity", "3650", "-storetype", "PKCS12",
                "-keystore", temporary.toString(), "-storepass:env", "BACAY_KEYTOOL_SECRET",
                "-dname", "CN=Ba Cay Server", "-ext", "SAN=" + subjectAlternativeNames(), "-noprompt");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("BACAY_KEYTOOL_SECRET", password);
        Process process = builder.start();
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Quá thời gian tạo chứng chỉ TLS");
            }
            if (process.exitValue() != 0) throw new IOException("Không tạo được keystore TLS; kiểm tra JDK/keytool và đường dẫn");
            restrict(temporary);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String subjectAlternativeNames() throws SocketException {
        Set<String> names = new LinkedHashSet<>(List.of("dns:localhost", "ip:127.0.0.1", "ip:::1"));
        var interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces != null) {
            for (NetworkInterface network : Collections.list(interfaces)) {
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isAnyLocalAddress()) names.add("ip:" + address.getHostAddress());
                }
            }
        }
        String additional = TlsTransport.setting("BACAY_TLS_SAN", "");
        if (!additional.isBlank()) names.addAll(Arrays.asList(additional.split(",")));
        return String.join(",", names);
    }

    private static void restrict(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Không dùng symlink cho khóa TLS");
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) throw new IOException("Không thiết lập được quyền riêng tư cho khóa TLS");
        AclEntry.Builder owner = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (Files.isDirectory(path)) owner.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
        acl.setAcl(List.of(owner.build()));
    }
}
