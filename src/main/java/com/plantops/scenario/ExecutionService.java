package com.plantops.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.*;
import com.plantops.domain.RescheduleLevel;
import com.plantops.persistence.entity.PlanDispatchEntity;
import com.plantops.persistence.entity.PlanningEventEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ExecutionService {

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    DetailScheduleService detailScheduleService;

    @Inject
    KittingService kittingService;

    @Inject
    MaterialFeasibilityService materialFeasibilityService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public DispatchResultDto dispatch(String planVersionId) {
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(planVersionId);
        if (version == null) {
            throw new IllegalArgumentException("Plan version not found: " + planVersionId);
        }
        PlanDispatchEntity dispatch = new PlanDispatchEntity();
        dispatch.planVersionId = planVersionId;
        dispatch.dispatchedTs = LocalDateTime.now();
        dispatch.targetSystem = "MES";
        dispatch.persist();
        return new DispatchResultDto(planVersionId, dispatch.dispatchedTs, "DISPATCHED");
    }

    @Transactional
    public RescheduleResultDto handleEvent(PlanningEventDto event) throws Exception {
        PlanningEventEntity entity = new PlanningEventEntity();
        entity.eventId = event.eventId() != null ? event.eventId() : "EVT-" + UUID.randomUUID().toString().substring(0, 8);
        entity.eventType = event.eventType();
        entity.eventTs = event.eventTs() != null ? event.eventTs() : LocalDateTime.now();
        entity.payloadJson = objectMapper.writeValueAsString(event.payload());
        entity.processed = false;
        entity.persist();

        RescheduleLevel level = determineLevel(event.eventType(), event.payload());
        entity.rescheduleLevel = level.name();
        entity.processed = true;

        RescheduleResultDto result = executeReschedule(level, event.payload());
        entity.rescheduleLevel = result.level().name();
        return result;
    }

    private RescheduleResultDto executeReschedule(RescheduleLevel level, Map<String, Object> payload) throws Exception {
        java.util.List<String> impacted = new java.util.ArrayList<>();
        if (payload != null && payload.containsKey("workOrderNo")) {
            impacted.add(String.valueOf(payload.get("workOrderNo")));
        }
        return switch (level) {
            case R0, R1 -> new RescheduleResultDto(level, null, null, impacted);
            case R2 -> {
                String mpId = payload != null ? String.valueOf(payload.getOrDefault("masterPlanVersionId", "")) : "";
                if (mpId.isBlank()) {
                    mpId = masterPlanService.solve().planVersionId();
                }
                DetailScheduleResultDto ds = detailScheduleService.solve(mpId);
                yield new RescheduleResultDto(level, mpId, ds.planVersionId(), impacted);
            }
            case R3 -> {
                materialFeasibilityService.prepareContext();
                MasterPlanResultDto mp = masterPlanService.solve();
                kittingService.compute();
                DetailScheduleResultDto ds = detailScheduleService.solve(mp.planVersionId());
                yield new RescheduleResultDto(level, mp.planVersionId(), ds.planVersionId(), impacted);
            }
        };
    }

    private RescheduleLevel determineLevel(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "EQUIPMENT_DOWN" -> RescheduleLevel.R2;
            case "MATERIAL_SHORTAGE" -> RescheduleLevel.R2;
            case "ORDER_EXPEDITE" -> RescheduleLevel.R3;
            case "MINOR_DELAY" -> RescheduleLevel.R1;
            default -> RescheduleLevel.R1;
        };
    }
}
