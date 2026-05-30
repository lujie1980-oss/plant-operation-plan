package com.plantops.scenario;

import com.plantops.api.dto.PlanVersionCompareDto;
import com.plantops.persistence.entity.PlanVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PlanVersionService {

    public PlanVersionCompareDto compare(String fromVersionId, String toVersionId) {
        PlanVersionEntity from = PlanVersionEntity.findByVersionId(fromVersionId);
        PlanVersionEntity to = PlanVersionEntity.findByVersionId(toVersionId);
        return new PlanVersionCompareDto(
                fromVersionId,
                toVersionId,
                from != null ? from.score : null,
                to != null ? to.score : null,
                List.of("Compare allocations via master_plan_allocation / detail_schedule_operation tables"));
    }
}
