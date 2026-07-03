package com.plantops.sample;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

@QuarkusTest
@TestProfile(ExportTestProfile.class)
class SampleDataExporterTest {

    @Inject
    SampleDataExporter sampleDataExporter;

    @Test
    void exportTeWorkspaceJson() throws Exception {
        Path out = Path.of("src/main/resources/sample-data/factory-te-demo.json");
        sampleDataExporter.writeJson("te", out);
    }
}
