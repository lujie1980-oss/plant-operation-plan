package com.plantops.scenario.planning.persist;

import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.scenario.MasterPlanService;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

/**
 * confirm 唯一写入口：将 Session optimize 结果持久化为 audit {@code MasterPlanAllocationEntity}。
 * 读路径仍由 {@link com.plantops.scenario.planning.PlanVersionAllocationHydrator} 反灌图。
 */
@ApplicationScoped
public class OntologyStatePersister {

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    public record SessionPersistRequest(
            String sessionId,
            String parentPlanVersionId,
            MasterPlanSolveProfile solveProfile,
            OptimizerResult optimizerResult) {
    }

    public record PersistOutcome(String planVersionId, int allocationCount) {
    }

    public PersistOutcome persistSession(SessionPersistRequest request) {
        if (request == null || request.optimizerResult() == null) {
            throw new BadRequestException("Call optimize before confirm");
        }
        if (request.optimizerResult().persistAllocations().isEmpty()) {
            throw new BadRequestException("No allocations to persist; call optimize first");
        }
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                request.solveProfile() != null ? request.solveProfile().strategyId() : null);
        String planVersionId = masterPlanService.persistFromAllocations(
                request.optimizerResult().persistAllocations(),
                request.optimizerResult().scoreSummary(),
                request.optimizerResult().solveDurationMs(),
                resolved,
                request.parentPlanVersionId());
        int allocationCount = (int) MasterPlanAllocationEntity.count("planVersionId = ?1", planVersionId);
        return new PersistOutcome(planVersionId, allocationCount);
    }
}
