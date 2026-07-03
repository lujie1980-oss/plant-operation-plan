package com.plantops.iam.module;

import com.plantops.testsupport.SpecRef;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-IAM-06: workspace-modules.yaml stays aligned with WorkspaceModuleCatalog. */
@QuarkusTest
@SpecRef("AC-IAM-06")
class WorkspaceModuleCatalogSyncTest {

    @Inject
    WorkspaceModuleRegistryValidator validator;

    @Test
    void yamlRegistryMatchesJavaCatalog() {
        var errors = validator.validate();
        assertTrue(errors.isEmpty(), () -> "Module registry drift:\n" + String.join("\n", errors));
    }
}
