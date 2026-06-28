package com.plantops.scenario;

import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;

@ApplicationScoped
public class UpstreamWorkOrderNumberAllocator {

    public String allocate(String productCode, LocalDate needDate) {
        return MrpExplosionService.allocateUniqueWorkOrderNo(
                productCode, needDate, WorkOrderEntity.nextSequenceNo());
    }
}
