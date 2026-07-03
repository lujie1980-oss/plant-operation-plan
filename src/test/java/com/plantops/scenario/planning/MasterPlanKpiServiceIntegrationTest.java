package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.MasterPlanKpiDtos.MasterPlanKpisResponseDto;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.testsupport.SpecRef;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@SpecRef("AC-MP-KPI-01")
class MasterPlanKpiServiceIntegrationTest {

    @Inject
    MasterPlanKpiService masterPlanKpiService;

    @Test
    @Transactional
    void returnsBusinessKpisForPersistedPlanVersion() {
        String versionId = "MP-KPI-T16";
        PlanVersionEntity version = new PlanVersionEntity();
        version.planVersionId = versionId;
        version.planType = "MASTER_PLAN";
        version.planGeneratedTs = LocalDateTime.now();
        version.changeSource = "TEST";
        version.solveDurationMs = 1234L;
        version.score = "0hard/-42soft";
        version.totalKpi = -42;
        version.stampWorkspace();
        version.persist();

        MasterPlanKpisResponseDto dto = masterPlanKpiService.getKpis(versionId);
        assertNotNull(dto.kpiBreakdown());
        assertEquals(-42, dto.totalKpi());
        assertEquals(10, dto.businessKpis().size());
        assertEquals("KPI-MP-B10", dto.businessKpis().get(9).kpiId());
        assertEquals(1234.0, dto.businessKpis().get(9).value());
    }
}
