package com.plantops.scenario.planning.persist;

import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.config.OntologySessionPersistenceFeature;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.scenario.MasterPlanService;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

/**
 * confirm 写入口：Session optimize 结果 → PlanVersion +（可选）legacy audit allocation。
 * <p>
 * <strong>TODO-22 R5：</strong>占用 SoT 为 committed {@code ont_resource_capacity_assignment}（经
 * {@link com.plantops.ontology.persistence.OntologySessionPersistenceService#promoteDraftToCommitted}）；
 * {@code master_plan_allocation} 仅在 session 持久化关闭时作为回退写入。
 */
@ApplicationScoped
public class OntologyStatePersister {

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    OntologySessionPersistenceFeature sessionPersistenceFeature;

    public record SessionPersistRequest(
            String sessionId,
            String parentPlanVersionId,
            MasterPlanSolveProfile solveProfile,
            OptimizerResult optimizerResult,
            OntologyGraph sessionGraph) {
    }

    public record PersistOutcome(String planVersionId, int occupancyCount) {
    }

    public PersistOutcome persistSession(SessionPersistRequest request) {
        if (request == null || request.optimizerResult() == null) {
            throw new BadRequestException("Call optimize before confirm");
        }
        if (request.sessionGraph() == null) {
            throw new BadRequestException("Session graph required for confirm");
        }

        int occupancyCount = request.sessionGraph().resourceCapacityAssignmentsById().size();
        if (sessionPersistenceFeature.enabled()) {
            if (occupancyCount == 0) {
                throw new BadRequestException("No ENT-RCA to persist; call optimize first");
            }
        } else if (request.optimizerResult().persistAllocations().isEmpty()) {
            throw new BadRequestException("No allocations to persist; call optimize first");
        }

        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                request.solveProfile() != null ? request.solveProfile().strategyId() : null);

        String planVersionId;
        if (sessionPersistenceFeature.enabled()) {
            planVersionId = masterPlanService.persistPlanVersionHeaderOnly(
                    request.optimizerResult().scoreSummary(),
                    request.optimizerResult().solveDurationMs(),
                    resolved,
                    request.parentPlanVersionId());
        } else {
            planVersionId = masterPlanService.persistFromAllocations(
                    request.optimizerResult().persistAllocations(),
                    request.optimizerResult().scoreSummary(),
                    request.optimizerResult().solveDurationMs(),
                    resolved,
                    request.parentPlanVersionId());
            occupancyCount = (int) MasterPlanAllocationEntity.count("planVersionId = ?1", planVersionId);
        }

        return new PersistOutcome(planVersionId, occupancyCount);
    }
}
