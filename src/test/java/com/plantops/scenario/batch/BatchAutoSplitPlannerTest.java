package com.plantops.scenario.batch;

import com.plantops.persistence.entity.WorkOrderEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchAutoSplitPlannerTest {

    @Test
    void computeTargetBatchSize_urgentNeedDateShrinksBatch() {
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.needDate = LocalDate.now().plusDays(2);
        int size = BatchAutoSplitPlanner.computeTargetBatchSize(
                wo, new BigDecimal("100"), 40, 10, 100, 480);
        assertTrue(size <= 20);
        assertTrue(size >= 10);
    }

    @Test
    void planQuantities_mergesSmallTailIntoPrevious() {
        List<BigDecimal> parts = BatchAutoSplitPlanner.planQuantities(
                new BigDecimal("105"), 40, 10, 100);
        assertEquals(3, parts.size());
        BigDecimal sum = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(new BigDecimal("105.0000")));
        assertTrue(parts.get(parts.size() - 1).compareTo(new BigDecimal("10")) >= 0);
    }

    @Test
    void planQuantities_singleBatchWhenUnderMax() {
        List<BigDecimal> parts = BatchAutoSplitPlanner.planQuantities(
                new BigDecimal("50"), 40, 10, 100);
        assertEquals(1, parts.size());
        assertEquals(0, parts.get(0).compareTo(new BigDecimal("50.0000")));
    }
}
