package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.rol.ChangeOperation;
import com.plantops.rol.ChangeSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyTimefoldMapperTest {

    private final OntologyTimefoldMapper mapper = new OntologyTimefoldMapper();

    @Test
    void toChangeSetProjectsAllocationsByPisppPeriod() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        String productCode = "FG-OTD-200";
        String pispId = OntologyIds.pispId(productCode);
        String pispp0Id = OntologyIds.pisppId(pispId, 0);
        String pispp2Id = OntologyIds.pisppId(pispId, 2);

        OntologyGraph graph = OntologyGraph.builder()
                .pisp(new ProductInStockingPoint(
                        pispId,
                        productCode,
                        OntologyIds.DEFAULT_FG,
                        productCode))
                .periodsOrdered(List.of(
                        new Period(OntologyIds.periodId(0), 0, planningStart, planningStart),
                        new Period(
                                OntologyIds.periodId(1),
                                1,
                                planningStart.plusDays(1),
                                planningStart.plusDays(1)),
                        new Period(
                                OntologyIds.periodId(2),
                                2,
                                planningStart.plusDays(2),
                                planningStart.plusDays(2))))
                .pispPeriod(new ProductInStockingPointPeriod(pispp0Id, pispId, OntologyIds.periodId(0)))
                .pispPeriod(new ProductInStockingPointPeriod(
                        OntologyIds.pisppId(pispId, 1), pispId, OntologyIds.periodId(1)))
                .pispPeriod(new ProductInStockingPointPeriod(pispp2Id, pispId, OntologyIds.periodId(2)))
                .build();

        List<MasterPlanAllocationDto> allocations = List.of(
                allocation("ALLOC-1", productCode, new BigDecimal("10"), planningStart),
                allocation("ALLOC-2", productCode, new BigDecimal("6.5"), planningStart.plusDays(2)));

        ChangeSet changeSet = mapper.toChangeSet(allocations, graph, PeriodIndex.of(graph.periodsOrdered()));
        assertEquals(2, changeSet.operations().size());

        Map<String, ChangeOperation> opByTarget = changeSet.operations().stream()
                .collect(Collectors.toMap(ChangeOperation::targetId, Function.identity()));
        ChangeOperation p0 = opByTarget.get(pispp0Id);
        ChangeOperation p2 = opByTarget.get(pispp2Id);
        assertNotNull(p0);
        assertNotNull(p2);
        assertEquals(ChangeOperation.TARGET_PRODUCT_IN_STOCKING_POINT_PERIOD, p0.targetType());
        assertEquals("plannedSupplyTotal", p0.property());
        assertEquals(10.0, ((Number) p0.value()).doubleValue(), 1e-6);
        assertEquals(6.5, ((Number) p2.value()).doubleValue(), 1e-6);
        assertTrue(changeSet.description().contains("allocations"));
    }

    private static MasterPlanAllocationDto allocation(
            String allocationId,
            String productCode,
            BigDecimal quantity,
            LocalDate slotDate) {
        LocalDateTime start = slotDate.atStartOfDay();
        return new MasterPlanAllocationDto(
                allocationId,
                0,
                "WO-" + allocationId,
                null,
                null,
                productCode,
                quantity,
                null,
                0,
                "RES-TEST",
                0,
                slotDate,
                "DAY",
                start,
                start.plusHours(8),
                480);
    }
}
