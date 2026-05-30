package com.plantops.scenario;

import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelOperationBindingServiceTest {

    @Test
    void orphan_allowsAlternateProductionLines() {
        OperationAssignment orphan = op("OP-3", "A", "Coaxial", 45);
        orphan.setParallelOrphan(true);
        orphan.setAllowedLineIds(List.of("YD-13", "YD-21"));

        assertTrue(orphan.acceptsLine(line("YD-21", "Coaxial")));
        assertTrue(orphan.acceptsLine(line("YD-13", "Coaxial")));
        assertFalse(orphan.acceptsLine(line("YD-99", "Coaxial")));
        assertFalse(orphan.acceptsLine(line("YD-13", "总成")));
    }

    @Test
    void pairedOperation_onlyAcceptsDesignatedLine() {
        OperationAssignment op = op("OP-4", "A", "Coaxial", 30);
        op.setParallelPaired(true);
        op.setDesignatedLineId("YD-13");
        op.setAllowedLineIds(List.of("YD-13"));

        assertTrue(op.acceptsLine(line("YD-13", "Coaxial")));
        assertFalse(op.acceptsLine(line("YD-21", "Coaxial")));
    }

    @Test
    void standardOperation_onlyAcceptsRoutingResourceLine() {
        OperationAssignment op = op("OP-5", "A", "Coaxial", 30);
        assertTrue(op.acceptsLine(line("YD-13", "Coaxial")));
        assertFalse(op.acceptsLine(line("YD-13", "总成")));
    }

    @Test
    void flexibleRouting_allowsAlternateResourcesOnDifferentLines() {
        OperationAssignment op = op("OP-6", "A", "RES-A", 30);
        op.setAllowedResourceIds(List.of("RES-A", "RES-B"));

        assertTrue(op.acceptsLine(line("LINE-A", "RES-A")));
        assertTrue(op.acceptsLine(line("LINE-B", "RES-B")));
        assertFalse(op.acceptsLine(line("LINE-C", "RES-C")));
    }

    private static OperationAssignment op(String id, String product, String resource, int minutes) {
        OperationAssignment op = new OperationAssignment();
        op.setOperationId(id);
        op.setProductCode(product);
        op.setResourceId(resource);
        op.setDurationMinutes(minutes);
        return op;
    }

    private static ScheduleLine line(String lineId, String resourceId) {
        return new ScheduleLine(lineId, resourceId, "A1", true, 480);
    }
}
