package com.plantops.sample;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Uses local file H2 (./data/plantops) for exporting workspace sample JSON. */
public class ExportTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.jdbc.url", "jdbc:h2:file:./data/plantops;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1",
                "quarkus.flyway.migrate-at-start", "false",
                "plantops.sample-data.enabled", "false");
    }
}
