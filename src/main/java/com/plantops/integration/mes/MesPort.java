package com.plantops.integration.mes;

import com.plantops.api.dto.PlanningEventDto;

public interface MesPort {
    void publishPlan(String planVersionId);

    PlanningEventDto pollFeedback();
}
