package com.plantops.scenario.planning;

import com.plantops.testsupport.SpecRef;
import com.plantops.api.dto.planning.MasterPlanKpiDtos.KpiBreakdownDto;
import com.plantops.api.dto.planning.PlanningConstraintMatchTotalDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpecRef("AC-MP-KPI-01")
class MasterPlanKpiBreakdownBuilderTest {

    @Test
    void aggregatesConstraintTotalsIntoDomains() {
        List<PlanningConstraintMatchTotalDto> totals = List.of(
                new PlanningConstraintMatchTotalDto(
                        "Minimize lateness", "", "Minimize lateness", 0, -120, 3, List.of(), false),
                new PlanningConstraintMatchTotalDto(
                        "Material feasible on slot date", "", "Material feasible on slot date", -2, 0, 2, List.of(), false),
                new PlanningConstraintMatchTotalDto(
                        "Slot capacity", "", "Slot capacity", 0, -40, 1, List.of(), false));

        KpiBreakdownDto breakdown = MasterPlanKpiBreakdownBuilder.fromConstraintTotals(totals);

        assertEquals(0, breakdown.delivery().hard());
        assertEquals(-120, breakdown.delivery().soft());
        assertEquals(-2, breakdown.material().hard());
        assertEquals(-40, breakdown.capacity().soft());
        assertNotNull(breakdown.scoring());
        assertEquals(1, breakdown.scoring().size());
        assertEquals(2, breakdown.constraint().size());
    }

    @Test
    void parsesTotalKpiFromScoreString() {
        assertEquals(-150, MasterPlanKpiBreakdownBuilder.totalKpiFromScore("0hard/-150soft"));
        assertEquals(-5, MasterPlanKpiBreakdownBuilder.totalKpiFromScore("-2hard/-3soft"));
    }
}
