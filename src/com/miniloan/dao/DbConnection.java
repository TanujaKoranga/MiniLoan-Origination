package com.miniloan.dao;

import com.miniloan.utility.ConfigLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens JDBC connections using db.url / db.user / db.password from config.properties. */
public class DbConnection {
    private final ConfigLoader config;

    public DbConnection(ConfigLoader config) {
        this.config = config;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "MySQL Connector/J not found on the classpath. Add mysql-connector-j-8.x.jar to -cp.", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(config.get("db.url"), config.get("db.user"), config.get("db.password", ""));
    }
}
