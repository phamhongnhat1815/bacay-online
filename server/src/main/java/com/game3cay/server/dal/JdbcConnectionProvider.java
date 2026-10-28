package com.game3cay.server.dal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opens independent JDBC connections and bounds concurrency using only the JDK. */
final class JdbcConnectionProvider implements ConnectionProvider {
    private final String url;
    private final Properties credentials = new Properties();
    private final Semaphore permits;
    private final long acquireTimeoutMs;
    private final Set<Lease> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    JdbcConnectionProvider(String url, String user, String password, int maximumConnections, long acquireTimeoutMs) {
        if (maximumConnections < 1 || acquireTimeoutMs < 1) throw new IllegalArgumentException("Giới hạn JDBC không hợp lệ");
        this.url = java.util.Objects.requireNonNull(url);
        credentials.setProperty("user", java.util.Objects.requireNonNull(user));
        credentials.setProperty("password", java.util.Objects.requireNonNull(password));
        credentials.setProperty("tcpKeepAlive", "true");
        permits = new Semaphore(maximumConnections, true);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    @Override public Connection open() throws SQLException {
        if (closed.get()) throw new SQLException("Dịch vụ kết nối JDBC đã đóng");
        try {
            if (!permits.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw new SQLTimeoutException("Quá thời gian chờ kết nối JDBC khả dụng");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Đã hủy chờ kết nối JDBC", e);
        }
        Lease lease = null;
        try {
            if (closed.get()) throw new SQLException("Dịch vụ kết nối JDBC đã đóng");
            lease = new Lease(DriverManager.getConnection(url, credentials));
            active.add(lease);
            if (closed.get()) {
                lease.close();
                throw new SQLException("Dịch vụ kết nối JDBC đã đóng");
            }
            Lease connectionLease = lease;
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("close")) { connectionLease.close(); return null; }
                        if (name.equals("isClosed")) return connectionLease.released.get() || connectionLease.connection.isClosed();
                        if (name.equals("equals")) return proxy == args[0];
                        if (name.equals("hashCode")) return System.identityHashCode(proxy);
                        if (name.equals("toString")) return "JdbcConnection[closed=" + connectionLease.released.get() + "]";
                        if (connectionLease.released.get()) throw new SQLException("Kết nối JDBC đã đóng");
                        if (name.equals("abort")) {
                            connectionLease.connection.abort((java.util.concurrent.Executor) args[0]);
                            connectionLease.close();
                            return null;
                        }
                        if ((name.equals("unwrap") || name.equals("isWrapperFor"))
                                && args[0] instanceof Class<?> type && type.isInstance(proxy)) {
                            return name.equals("unwrap") ? proxy : true;
                        }
                        try { return method.invoke(connectionLease.connection, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        } catch (SQLException | RuntimeException e) {
            if (lease == null) permits.release();
            else {
                try { lease.close(); } catch (SQLException cleanup) { e.addSuppressed(cleanup); }
            }
            throw e;
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (Lease lease : active) {
            try { lease.close(); }
            catch (SQLException e) { java.util.logging.Logger.getLogger(getClass().getName()).warning("Không đóng được kết nối JDBC: " + e.getSQLState()); }
        }
    }

    private final class Lease {
        final Connection connection;
        final AtomicBoolean released = new AtomicBoolean();
        Lease(Connection connection) { this.connection = connection; }
        void close() throws SQLException {
            if (!released.compareAndSet(false, true)) return;
            SQLException failure = null;
            try { if (!connection.isClosed() && !connection.getAutoCommit()) connection.rollback(); }
            catch (SQLException e) { failure = e; }
            try { connection.close(); }
            catch (SQLException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
            finally { active.remove(this); permits.release(); }
            if (failure != null) throw failure;
        }
    }
}
