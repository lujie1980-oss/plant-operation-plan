package com.plantops.scenario;

import com.plantops.testsupport.SpecRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpecRef("AC-VAL-06-01")
class ScenarioComparisonServiceTest {

    @Test
    void coldMetricIdPrefixesDeliveryKpi() {
        assertEquals("cold_total_deliveries", ScenarioComparisonService.coldMetricId("TOTAL_DELIVERIES"));
        assertEquals("cold_shortage", ScenarioComparisonService.coldMetricId("SHORTAGE"));
    }

    @Test
    void businessMetricIdMapsKpiMpBxx() {
        assertEquals("mp_b01", ScenarioComparisonService.businessMetricId("KPI-MP-B01"));
        assertEquals("mp_b10", ScenarioComparisonService.businessMetricId("KPI-MP-B10"));
    }
}
