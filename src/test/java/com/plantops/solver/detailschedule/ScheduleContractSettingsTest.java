package com.plantops.solver.detailschedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleContractSettingsTest {

    @Test
    void dueDatePenalty_onlyWhenLate() {
        ScheduleContractSettings settings = ScheduleContractSettings.defaults();
        LocalDate due = LocalDate.of(2026, 6, 1);
        assertEquals(0, settings.dueDatePenalty(due, due));
        assertEquals(0, settings.dueDatePenalty(due, due.minusDays(1)));
        assertEquals(100, settings.dueDatePenalty(due, due.plusDays(1)));
        assertEquals(300, settings.dueDatePenalty(due, due.plusDays(3)));
    }

    @Test
    void masterPlanPenalty_earlyAndLateUseDifferentWeights() {
        ScheduleContractSettings settings = new ScheduleContractSettings(
                100, 20, 8,
                ScheduleContractPenaltyMode.LINEAR,
                ScheduleContractPenaltyMode.LINEAR,
                3);
        LocalDate target = LocalDate.of(2026, 6, 10);
        assertEquals(16, settings.masterPlanTargetPenalty(target, target.minusDays(2)));
        assertEquals(40, settings.masterPlanTargetPenalty(target, target.plusDays(2)));
        assertTrue(settings.masterPlanTargetPenalty(target, target.plusDays(2))
                > settings.masterPlanTargetPenalty(target, target.minusDays(2)));
    }

    @Test
    void masterPlanPenalty_lateQuadratic() {
        ScheduleContractSettings settings = new ScheduleContractSettings(
                100, 20, 8,
                ScheduleContractPenaltyMode.QUADRATIC,
                ScheduleContractPenaltyMode.LINEAR,
                3);
        LocalDate target = LocalDate.of(2026, 6, 10);
        assertEquals(80, settings.masterPlanTargetPenalty(target, target.plusDays(2)));
    }

    @Test
    void masterPlanPenalty_earlyCapped() {
        ScheduleContractSettings settings = new ScheduleContractSettings(
                100, 20, 8,
                ScheduleContractPenaltyMode.LINEAR,
                ScheduleContractPenaltyMode.CAPPED,
                2);
        LocalDate target = LocalDate.of(2026, 6, 10);
        assertEquals(16, settings.masterPlanTargetPenalty(target, target.minusDays(5)));
    }
}
