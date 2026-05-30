package com.plantops.integration.mes;

import com.plantops.api.dto.PlanningEventDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class MockMesAdapter implements MesPort {

    @Override
    public void publishPlan(String planVersionId) {
        // Mock: no-op
    }

    @Override
    public PlanningEventDto pollFeedback() {
        return null;
    }

    public PlanningEventDto equipmentDown(String resourceId) {
        return new PlanningEventDto(
                null,
                "EQUIPMENT_DOWN",
                null,
                Map.of("resourceId", resourceId));
    }
}
