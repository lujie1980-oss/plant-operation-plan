package com.plantops.scenario;

import com.plantops.api.dto.planning.OrderPlanningChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainNodeDto;
import com.plantops.api.dto.planning.OrderPlanningChainSummaryDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderDemandActionServiceTest {

    @Test
    void suggestPromiseDate_usesLatestWorkOrderWindowEnd() {
        OrderPlanningChainDto chain = new OrderPlanningChainDto(
                "SO-1",
                10,
                "P-1",
                LocalDate.of(2026, 6, 30),
                null,
                "OK",
                "OK",
                new OrderPlanningChainSummaryDto("FINITE_CAPACITY", null, 2, 4, Map.of(), Instant.now()),
                List.of(
                        node("SO", "SALES_ORDER", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 20)),
                        node("WO1", "WORK_ORDER", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)),
                        node("WO2", "WORK_ORDER", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 25))),
                List.of(),
                null);

        assertEquals(LocalDate.of(2026, 6, 25), OrderDemandActionService.suggestPromiseDate(chain));
    }

    @Test
    void suggestPromiseDate_nullWhenNoWindows() {
        assertNull(OrderDemandActionService.suggestPromiseDate(null));
    }

    private static OrderPlanningChainNodeDto node(
            String id,
            String type,
            LocalDate start,
            LocalDate end) {
        return new OrderPlanningChainNodeDto(
                id,
                type,
                "lane",
                id,
                "OK",
                0,
                "P",
                BigDecimal.ONE,
                start,
                end,
                "S04",
                List.of(),
                Map.of(),
                List.of());
    }
}
