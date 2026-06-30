package com.plantops.ontology.persistence.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Quarkus test profile targeting local PostgreSQL (docker-compose.postgres.yml). */
public class PostgresOntologyTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.datasource.jdbc.url", PostgresTestSupport.jdbcUrl(),
                "quarkus.datasource.username", PostgresTestSupport.jdbcUser(),
                "quarkus.datasource.password", PostgresTestSupport.jdbcPassword(),
                "quarkus.flyway.locations", "classpath:db/migration-postgresql",
                "quarkus.flyway.migrate-at-start", "true",
                "plantops.sample-data.enabled", "false",
                "plantops.legacy-schema.enabled", "false");
    }
}
