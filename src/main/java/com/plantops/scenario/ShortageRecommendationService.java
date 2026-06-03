package com.plantops.scenario;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.ShortageRecommendationDto;
import com.plantops.domain.ShortageType;
import com.plantops.persistence.entity.ShortageRecommendationEntity;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ShortageRecommendationService {

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public List<ShortageRecommendationDto> analyze(DetailSchedule schedule, String planVersionId) {
        List<ShortageRecommendationDto> results = new ArrayList<>();
        if (schedule.score() != null && schedule.score().isFeasible()) {
            return results;
        }

        for (OperationAssignment op : schedule.getOperations()) {
            if (op.getLine() == null) {
                results.add(persist(planVersionId, ShortageType.CAPACITY, "HIGH", op,
                        "USE_ALTERNATE_RESOURCE", Map.of("reason", "No line assigned")));
            } else if (op.getEndMinute() != null && op.getLine() != null) {
                int lineCap = op.getLine().getCapacityMinutes();
                if (lineCap > 0 && op.getEndMinute() > lineCap) {
                    results.add(persist(planVersionId, ShortageType.CAPACITY, "HIGH", op,
                            "RESEQUENCE", Map.of("overflowMinutes", op.getEndMinute() - lineCap)));
                }
            } else if (op.getLine() != null && !op.getLine().isOpened()) {
                results.add(persist(planVersionId, ShortageType.HEADCOUNT, "MEDIUM", op,
                        "CHANGE_OPEN_LINES", Map.of("lineId", op.getLine().getLineId())));
            }
        }
        return results;
    }

    private ShortageRecommendationDto persist(
            String planVersionId,
            ShortageType type,
            String severity,
            OperationAssignment op,
            String action,
            Map<String, Object> evidence) {
        String shortageId = "SH-" + UUID.randomUUID().toString().substring(0, 8);
        String impactJson;
        String evidenceJson;
        try {
            impactJson = objectMapper.writeValueAsString(List.of(op.getWorkOrderNo()));
            evidenceJson = objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            impactJson = "[]";
            evidenceJson = "{}";
        }

        ShortageRecommendationEntity entity = new ShortageRecommendationEntity();
        entity.shortageId = shortageId;
        entity.planVersionId = planVersionId;
        entity.shortageType = type.name();
        entity.severity = severity;
        entity.areaId = op.getLine() != null ? op.getLine().getAreaId() : null;
        entity.shiftId = "DAY";
        entity.lineId = op.getLine() != null ? op.getLine().getLineId() : null;
        entity.evidenceJson = evidenceJson;
        entity.recommendedAction = action;
        entity.impactOrdersJson = impactJson;
        entity.createdTs = LocalDateTime.now();
        entity.stampWorkspace();
        entity.persist();

        return new ShortageRecommendationDto(
                shortageId,
                type.name(),
                severity,
                entity.areaId,
                entity.shiftId,
                entity.lineId,
                evidence,
                action,
                List.of(op.getWorkOrderNo()));
    }
}
