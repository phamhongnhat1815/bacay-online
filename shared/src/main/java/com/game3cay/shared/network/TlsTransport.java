package com.game3cay.shared.network;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** TLS is enabled unless the operator explicitly opts out for a controlled local environment. */
public final class TlsTransport {
    private TlsTransport() {}

    public static String setting(String environment, String fallback) {
        return System.getProperty(environment, System.getenv().getOrDefault(environment, fallback));
    }

    public static boolean enabled() {
        String value = setting("BACAY_TLS_ENABLED", "true");
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("BACAY_TLS_ENABLED phải là true hoặc false");
        }
        return Boolean.parseBoolean(value);
    }

    public static Socket secureClient(Socket connected, String host, int handshakeTimeoutMs) throws IOException {
        if (!enabled()) return connected;
        try {
            SSLContext context = clientContext();
            SSLSocket secured = (SSLSocket) context.getSocketFactory().createSocket(
                    connected, host, connected.getPort(), true);
            secured.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            SSLParameters parameters = secured.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            secured.setSSLParameters(parameters);
            secured.setSoTimeout(handshakeTimeoutMs);
            secured.startHandshake();
            return secured;
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            try { connected.close(); } catch (IOException ignored) {}
            throw new IOException("Không xác minh được TLS máy chủ. Cấu hình BACAY_TLS_CERT bằng chứng chỉ tin cậy của máy chủ.", e);
        }
    }

    private static SSLContext clientContext() throws GeneralSecurityException, IOException {
        String configured = setting("BACAY_TLS_CERT", "");
        Path certificate = configured.isBlank() ? Path.of("data/tls/server.crt") : Path.of(configured);
        if (configured.isBlank() && !Files.isRegularFile(certificate)) return SSLContext.getDefault();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate trusted;
        try (var input = Files.newInputStream(certificate)) {
            trusted = (X509Certificate) factory.generateCertificate(input);
        }
        trusted.checkValidity();
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        trust.setCertificateEntry("bacay-server", trusted);
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return context;
    }
}
