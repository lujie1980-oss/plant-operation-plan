package com.plantops.scenario;

import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FulfillmentChainPromiseDateTest {

    @Test
    void suggest_usesLatestSupplyOrderEndTs() {
        OrderFulfillmentChainDto chain = new OrderFulfillmentChainDto(
                "SO-1",
                10,
                "P-1",
                LocalDate.of(2026, 6, 30),
                null,
                "PLANNED",
                "OK",
                List.of(
                        node("SO", "SALES_ORDER", LocalDateTime.of(2026, 6, 1, 17, 0)),
                        node("SO1", "SUPPLY_ORDER", LocalDateTime.of(2026, 6, 15, 17, 0)),
                        node("SO2", "SUPPLY_ORDER", LocalDateTime.of(2026, 6, 25, 17, 0))),
                List.of(),
                List.of(),
                "delivery-1");

        assertEquals(LocalDate.of(2026, 6, 25), FulfillmentChainPromiseDate.suggest(chain));
    }

    @Test
    void suggest_nullWhenNoEndDates() {
        assertNull(FulfillmentChainPromiseDate.suggest(null));
    }

    private static FulfillmentChainNodeDto node(String id, String type, LocalDateTime end) {
        return new FulfillmentChainNodeDto(
                id,
                type,
                type,
                id,
                "OK",
                0,
                "P",
                BigDecimal.ONE,
                end.minusDays(1),
                end,
                Map.of(),
                List.of());
    }
}
