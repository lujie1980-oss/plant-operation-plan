package com.plantops.ontology.persistence.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;

/** Gate PostgreSQL integration tests (docker-compose.postgres.yml on :5432). */
public final class PostgresTestSupport {

    private static final String JDBC =
            System.getenv().getOrDefault(
                    "DB_JDBC_URL", "jdbc:postgresql://localhost:5432/plantops");

    private PostgresTestSupport() {}

    public static boolean isAvailable() {
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection c = DriverManager.getConnection(JDBC, jdbcUser(), jdbcPassword())) {
                return c.isValid(3);
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static String jdbcUrl() {
        return JDBC;
    }

    public static String jdbcUser() {
        return System.getenv().getOrDefault("DB_USER", "plantops");
    }

    public static String jdbcPassword() {
        return System.getenv().getOrDefault("DB_PASSWORD", "plantops");
    }
}
