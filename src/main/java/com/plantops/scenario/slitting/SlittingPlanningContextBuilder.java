package com.plantops.scenario.slitting;

import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.IntermediateRollCatalogEntity;
import com.plantops.persistence.entity.MasterRollEntity;
import com.plantops.persistence.entity.SlittingAssignmentEntity;
import com.plantops.persistence.entity.SlittingPlanChildOrderEntity;
import com.plantops.persistence.entity.SlittingPlanMasterRollEntity;
import com.plantops.persistence.entity.SlittingPlanVersionEntity;
import com.plantops.persistence.entity.SlittingRollNodeEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class SlittingPlanningContextBuilder {

    public SlittingPlanningContext build(String planVersionId) {
        SlittingPlanVersionEntity plan = SlittingPlanVersionEntity.findByPlanVersionId(planVersionId);
        if (plan == null) {
            throw new NotFoundException("slitting plan not found: " + planVersionId);
        }
        List<MasterRollEntity> masterRolls = new ArrayList<>();
        for (SlittingPlanMasterRollEntity link : SlittingPlanMasterRollEntity.listByPlanVersionId(planVersionId)) {
            MasterRollEntity roll = MasterRollEntity.findById(link.masterRollId);
            if (roll != null) {
                masterRolls.add(roll);
            }
        }
        List<ChildSlittingOrderEntity> childOrders = new ArrayList<>();
        for (SlittingPlanChildOrderEntity link : SlittingPlanChildOrderEntity.listByPlanVersionId(planVersionId)) {
            ChildSlittingOrderEntity order = ChildSlittingOrderEntity.findById(link.childSlittingOrderId);
            if (order != null) {
                childOrders.add(order);
            }
        }
        return new SlittingPlanningContext(
                planVersionId,
                masterRolls,
                childOrders,
                IntermediateRollCatalogEntity.listActiveInWorkspace(),
                SlittingRollNodeEntity.listByPlanVersionId(planVersionId),
                SlittingAssignmentEntity.listByPlanVersionId(planVersionId));
    }
}
