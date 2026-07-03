package com.plantops.scenario.slitting;

import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.IntermediateRollCatalogEntity;
import com.plantops.persistence.entity.MasterRollEntity;
import com.plantops.persistence.entity.SlittingAssignmentEntity;
import com.plantops.persistence.entity.SlittingRollNodeEntity;

import java.util.List;

public record SlittingPlanningContext(
        String planVersionId,
        List<MasterRollEntity> masterRolls,
        List<ChildSlittingOrderEntity> childOrders,
        List<IntermediateRollCatalogEntity> catalog,
        List<SlittingRollNodeEntity> existingNodes,
        List<SlittingAssignmentEntity> existingAssignments) {
}
