package com.plantops.scenario.planning;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MasterPlanContractLoaderTest {

    private final MasterPlanContractLoader loader = new MasterPlanContractLoader();

    @Test
    void parseOperationKey_extractsSeqAndOrdinal() {
        MasterPlanContractLoader.OperationContract contract = new MasterPlanContractLoader.OperationContract(
                "RES-1", LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 13));
        var contracts = java.util.Map.of(
                MasterPlanContractLoader.contractKey("WO-1", 20, 1), contract);

        MasterPlanContractLoader.OperationContract resolved = MasterPlanContractLoader.resolveForStep(
                contracts, "WO-1", 20, 1);

        assertNotNull(resolved);
        assertEquals("RES-1", resolved.resourceId());
        assertEquals(LocalDate.of(2026, 6, 12), resolved.startDate());
    }

    @Test
    void resolveForStep_fallsBackToOrdinalZero() {
        MasterPlanContractLoader.OperationContract contract = new MasterPlanContractLoader.OperationContract(
                "RES-2", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10));
        var contracts = java.util.Map.of(
                MasterPlanContractLoader.contractKey("WO-2", 10, 0), contract);

        assertEquals(contract, MasterPlanContractLoader.resolveForStep(contracts, "WO-2", 10, 3));
    }

    @Test
    void computeFallbackTargetEndDate_stepsBackFromWorkOrderEnd() {
        LocalDate anchor = LocalDate.of(2026, 6, 1);
        LocalDate target = MasterPlanContractLoader.computeFallbackTargetEndDate(
                LocalDate.of(2026, 6, 12), 20, 30, anchor);
        assertEquals(LocalDate.of(2026, 6, 2), target);
    }

    @Test
    void computeFallbackTargetEndDate_notBeforeAnchor() {
        LocalDate anchor = LocalDate.of(2026, 6, 10);
        LocalDate target = MasterPlanContractLoader.computeFallbackTargetEndDate(
                LocalDate.of(2026, 6, 12), 10, 30, anchor);
        assertEquals(anchor, target);
    }

    @Test
    void load_blankVersion_returnsEmptySnapshot() {
        MasterPlanContractLoader.ContractSnapshot snapshot = loader.load("");
        assertNotNull(snapshot);
        assertEquals(0, snapshot.workOrderEndByWorkOrder().size());
        assertEquals(0, snapshot.operationContracts().size());
    }

    @Test
    void computeFallbackTargetEndDate_nullWorkOrderEnd() {
        assertNull(MasterPlanContractLoader.computeFallbackTargetEndDate(
                null, 10, 20, LocalDate.now()));
    }
}
