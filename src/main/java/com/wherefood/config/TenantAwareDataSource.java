package com.wherefood.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Applies the authenticated couple to every PostgreSQL connection before use. */
public final class TenantAwareDataSource extends AbstractDataSource {
    private final DataSource delegate;

    public TenantAwareDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return prepare(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return prepare(delegate.getConnection(username, password));
    }

    private Connection prepare(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select set_config('app.couple_id', ?, false)")) {
            statement.setString(1, CoupleContext.current() == null ? "" : CoupleContext.current().toString());
            statement.execute();
        }
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        clear(connection);
                        return method.invoke(connection, args);
                    }
                    if (method.getName().equals("prepareStatement")
                            || method.getName().equals("prepareCall")
                            || method.getName().equals("createStatement")) {
                        applyCurrentCouple(connection);
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static void applyCurrentCouple(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select set_config('app.couple_id', ?, false)")) {
            statement.setString(1, CoupleContext.current() == null ? "" : CoupleContext.current().toString());
            statement.execute();
        }
    }

    private static void clear(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("select set_config('app.couple_id', '', false)")) {
            statement.execute();
        } catch (SQLException ignored) {
            // The connection is going back to the pool and will be overwritten on next checkout.
        }
    }
}
