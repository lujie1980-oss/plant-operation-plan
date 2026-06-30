package com.plantops.scenario;

import com.plantops.api.dto.SrpCapacityCellDto;
import com.plantops.api.dto.SrpCapacityGanttDto;
import com.plantops.config.ParameterRegistry;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 从本体 {@link StandardResourcePeriod} 投影工序甘特/产能视图的日粒度利用率。
 */
@ApplicationScoped
public class StandardResourcePeriodGanttService {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    ParameterRegistry parameters;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public SrpCapacityGanttDto buildForMasterPlan(String masterPlanVersionId) {
        OntologyGraph graph = authoritativeOntologyGraph.getSrpCapacityOrLoad(
                WorkspaceResolver.currentWorkspaceId(), masterPlanVersionId);
        return project(graph, parameters.getInt("capacity_overload_threshold_pct", 110));
    }

    static SrpCapacityGanttDto project(OntologyGraph graph, int overloadThresholdPct) {
        List<Period> periods = graph.periodsOrdered();
        if (periods.isEmpty()) {
            LocalDate today = LocalDate.now();
            return new SrpCapacityGanttDto(today, today, List.of());
        }
        LocalDate horizonStart = periods.get(0).getStartDate();
        LocalDate horizonEnd = periods.get(periods.size() - 1).getEndDate();
        List<SrpCapacityCellDto> cells = new ArrayList<>();
        for (StandardResourcePeriod srp : graph.srpById().values()) {
            Period period = periodFor(graph, srp.getPeriodId());
            if (period == null) {
                continue;
            }
            int available = (int) Math.round(Math.max(0, srp.getAvailableCapacity()));
            int reserved = (int) Math.round(Math.max(0, srp.getReservedCapacity()));
            int utilization = available <= 0 ? (reserved > 0 ? 100 : 0) : (int) (reserved * 100L / available);
            boolean overloaded = utilization >= overloadThresholdPct;
            for (LocalDate d = period.getStartDate(); !d.isAfter(period.getEndDate()); d = d.plusDays(1)) {
                cells.add(new SrpCapacityCellDto(
                        srp.getStandardResourceId(),
                        d,
                        available,
                        reserved,
                        utilization,
                        overloaded));
            }
        }
        return new SrpCapacityGanttDto(horizonStart, horizonEnd, cells);
    }

    static Period periodFor(OntologyGraph graph, String periodId) {
        if (periodId == null) {
            return null;
        }
        for (Period period : graph.periodsOrdered()) {
            if (periodId.equals(period.getId())) {
                return period;
            }
        }
        return null;
    }
}
