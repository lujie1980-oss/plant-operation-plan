package com.plantops.scenario;

import com.plantops.api.dto.KpiMetricDto;
import com.plantops.api.dto.KpiReportDto;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class KpiService {

    public KpiReportDto report() {
        List<KpiMetricDto> metrics = new ArrayList<>();

        long totalOrders = SalesOrderLineEntity.countInWorkspace();
        long kittingOk = KittingResultEntity.find("workspaceId = ?1 and kittingStatus = ?2", KittingResultEntity.ws(), "KITTING_OK").count();
        double kittingRate = totalOrders == 0 ? 0 : (double) kittingOk / totalOrders * 100;
        metrics.add(new KpiMetricDto("kitting_rate_pct", kittingRate, "%"));

        PlanVersionEntity lastMp = PlanVersionEntity.find(
                "workspaceId = ?1 and planType = ?2 order by planGeneratedTs desc",
                PlanVersionEntity.ws(), "MASTER_PLAN")
                .firstResult();
        if (lastMp != null && lastMp.solveDurationMs != null) {
            metrics.add(new KpiMetricDto("master_plan_solve_ms", lastMp.solveDurationMs.doubleValue(), "ms"));
            metrics.add(new KpiMetricDto("master_plan_score", 0, lastMp.score != null ? lastMp.score : "N/A"));
        }

        PlanVersionEntity lastDs = PlanVersionEntity.find(
                "workspaceId = ?1 and planType = ?2 order by planGeneratedTs desc",
                PlanVersionEntity.ws(), "DETAIL_SCHEDULE")
                .firstResult();
        if (lastDs != null && lastDs.solveDurationMs != null) {
            metrics.add(new KpiMetricDto("detail_schedule_solve_ms", lastDs.solveDurationMs.doubleValue(), "ms"));
        }

        metrics.add(new KpiMetricDto("open_orders", totalOrders, "count"));
        return new KpiReportDto(metrics);
    }
}
