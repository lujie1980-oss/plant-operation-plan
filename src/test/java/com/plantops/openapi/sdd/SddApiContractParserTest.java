package com.plantops.openapi.sdd;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SddApiContractParserTest {

    @Test
    void parsesCorePlanningContracts() throws Exception {
        List<SddApiOperation> ops =
                SddApiContractParser.parse(Path.of("docs/sdd/core/06-api-contracts.md"));

        assertFalse(ops.isEmpty());
        assertTrue(contains(ops, "API-FC-01", "GET",
                "/api/v1/ontology/fulfillment/deliveries/{deliveryId}/fulfillment-chain"));
        assertTrue(contains(ops, "API-MP-02", "POST", "/api/v1/planning/master-plan/solve"));
        assertTrue(contains(ops, "API-MAT-04", "GET",
                "/api/v1/ontology/material-planning/pisps/{pispId}/period-demands"));
        assertTrue(contains(ops, "API-IAM-01", "GET", "/api/v1/iam/me"));

        Set<String> apiIds = ops.stream().map(SddApiOperation::apiId).collect(Collectors.toSet());
        assertTrue(apiIds.contains("API-INT-08"));
        assertTrue(apiIds.size() >= 30, "expected broad §6 coverage, got " + apiIds.size());
    }

    private static boolean contains(
            List<SddApiOperation> ops, String apiId, String method, String path) {
        return ops.stream().anyMatch(op -> op.apiId().equals(apiId)
                && op.httpMethod().equals(method)
                && op.path().equals(path));
    }
}
