package com.plantops.ontology.scheduling;

import com.plantops.api.dto.DetailScheduleResultDto;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ScheduleFeedbackEntity;
import com.plantops.scenario.DetailScheduleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;

/** Loads SCH-P0 ontology view from persisted detail schedule (read-only; no {@code ont_*} writes). */
@ApplicationScoped
public class DetailScheduleOntologyLoader {

    @Inject
    DetailScheduleService detailScheduleService;

    public DetailScheduleOntologyView load(String detailScheduleVersionId) {
        DetailScheduleResultDto result = detailScheduleService.get(detailScheduleVersionId);
        LocalDate anchor = resolvePlanningAnchor(detailScheduleVersionId);
        return DetailScheduleLegacyProjector.project(
                detailScheduleVersionId, anchor, result.operations());
    }

    static LocalDate resolvePlanningAnchor(String detailScheduleVersionId) {
        ScheduleFeedbackEntity feedback = ScheduleFeedbackEntity.find(
                        "workspaceId = ?1 and detailScheduleVersionId = ?2",
                        ScheduleFeedbackEntity.ws(),
                        detailScheduleVersionId)
                .firstResult();
        if (feedback != null && feedback.planningAnchorDate != null) {
            return feedback.planningAnchorDate;
        }
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(detailScheduleVersionId);
        if (version == null || !"DETAIL_SCHEDULE".equals(version.planType)) {
            throw new NotFoundException("Detail schedule version not found: " + detailScheduleVersionId);
        }
        if (version.planGeneratedTs != null) {
            return version.planGeneratedTs.toLocalDate();
        }
        return LocalDate.now();
    }
}
