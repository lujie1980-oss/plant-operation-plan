package com.plantops.ontology.persistence;

import com.plantops.ontology.persistence.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies V65 ont_* DDL on PostgreSQL (requires {@code docker-compose.postgres.yml}).
 */
@EnabledIf("com.plantops.ontology.persistence.support.PostgresTestSupport#isAvailable")
class OntP0SchemaMigrationTest {

    private static final String[] P0_TABLES = {
        "ont_revision",
        "ont_revision_head",
        "ont_change_log",
        "ont_session",
        "ont_demand",
        "ont_supply_order",
        "ont_operation",
        "ont_fulfillment",
        "ont_pispp",
        "ont_period",
        "ont_srp",
        "ont_resource_capacity_assignment"
    };

    @Test
    void flywayMigratesOntP0Tables() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        PostgresTestSupport.jdbcUrl(),
                        PostgresTestSupport.jdbcUser(),
                        PostgresTestSupport.jdbcPassword())
                .locations("classpath:db/migration-postgresql")
                .load();
        flyway.migrate();

        try (Connection c = DriverManager.getConnection(
                        PostgresTestSupport.jdbcUrl(),
                        PostgresTestSupport.jdbcUser(),
                        PostgresTestSupport.jdbcPassword());
                Statement s = c.createStatement()) {
            for (String table : P0_TABLES) {
                try (ResultSet rs = s.executeQuery(
                        "SELECT 1 FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_name = '" + table + "'")) {
                    assertTrue(rs.next(), "missing table: " + table);
                }
            }
        }
    }
}
