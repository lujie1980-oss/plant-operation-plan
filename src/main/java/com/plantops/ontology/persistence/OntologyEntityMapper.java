package com.plantops.ontology.persistence;

import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodGranularity;
import com.plantops.ontology.period.PhysicalResourcePeriod;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.persistence.entity.OntDemandEntity;
import com.plantops.ontology.persistence.entity.OntFulfillmentEntity;
import com.plantops.ontology.persistence.entity.OntOperationEntity;
import com.plantops.ontology.persistence.entity.OntPeriodEntity;
import com.plantops.ontology.persistence.entity.OntPisppEntity;
import com.plantops.ontology.persistence.entity.OntPrpEntity;
import com.plantops.ontology.persistence.entity.OntResourceCapacityAssignmentEntity;
import com.plantops.ontology.persistence.entity.OntSrpEntity;
import com.plantops.ontology.persistence.entity.OntSupplyOrderEntity;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.ResourceCapacityAssignment;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.SupplyOrderStatus;
import com.plantops.ontology.supply.SupplyOrderType;

/** Maps between ont_* JPA rows and in-memory ontology POJOs (§5.14.2 · V65 P0). */
public final class OntologyEntityMapper {

    private OntologyEntityMapper() {}

    public static Demand toDemand(OntDemandEntity row) {
        Demand d = new Demand();
        d.setId(row.entityId);
        d.setProductCode(row.productCode);
        d.setPispId(row.pispId);
        d.setQuantity(row.quantity);
        d.setNeedDate(row.needDate);
        d.setPriority(row.priority);
        d.setSourceType(DemandSourceType.valueOf(row.sourceType));
        d.setSourceId(row.sourceId);
        return d;
    }

    public static OntDemandEntity fromDemand(Demand d, String workspaceId, String revisionId) {
        OntDemandEntity row = new OntDemandEntity();
        row.stampKeys(workspaceId, revisionId, d.getId());
        row.productCode = d.getProductCode();
        row.pispId = d.getPispId();
        row.quantity = d.getQuantity();
        row.needDate = d.getNeedDate();
        row.priority = d.getPriority();
        row.sourceType = d.getSourceType().name();
        row.sourceId = d.getSourceId();
        return row;
    }

    public static SupplyOrder toSupplyOrder(OntSupplyOrderEntity row) {
        SupplyOrder so = new SupplyOrder();
        so.setId(row.entityId);
        so.setProductCode(row.productCode);
        so.setPispId(row.pispId);
        so.setQuantity(row.quantity);
        so.setNeedDate(row.needDate);
        so.setStatus(SupplyOrderStatus.valueOf(row.status));
        so.setType(SupplyOrderType.valueOf(row.type));
        return so;
    }

    public static OntSupplyOrderEntity fromSupplyOrder(
            SupplyOrder so, String workspaceId, String revisionId) {
        OntSupplyOrderEntity row = new OntSupplyOrderEntity();
        row.stampKeys(workspaceId, revisionId, so.getId());
        row.productCode = so.getProductCode();
        row.pispId = so.getPispId();
        row.quantity = so.getQuantity();
        row.needDate = so.getNeedDate();
        row.status = so.getStatus().name();
        row.type = so.getType().name();
        return row;
    }

    public static Operation toOperation(OntOperationEntity row) {
        Operation op = new Operation();
        op.setId(row.entityId);
        op.setSupplyOrderId(row.supplyOrderId);
        op.setPlanUnitId(row.planUnitId);
        op.setSequenceNr(row.sequenceNr);
        op.setRoutingSequenceNo(row.routingSequenceNo);
        op.setOperationName(row.operationName);
        op.setProductionDuration(row.productionDuration);
        op.setPreprocessingTime(row.preprocessingTime);
        op.setPostprocessingTime(row.postprocessingTime);
        op.setSegmentIndex(row.segmentIndex);
        op.setLastSegment(row.lastSegment);
        op.setParallelGroupId(row.parallelGroupId);
        op.setLocked(row.locked);
        op.setEarliestPossibleStartOwn(row.earliestPossibleStartOwn);
        op.setEarliestPossibleEndOwn(row.earliestPossibleEndOwn);
        op.setEarliestPossibleStartTotal(row.earliestPossibleStartTotal);
        op.setEarliestPossibleEndTotal(row.earliestPossibleEndTotal);
        op.setLatestDesiredStart(row.latestDesiredStart);
        op.setLatestDesiredEnd(row.latestDesiredEnd);
        op.setPlannedStartTotal(row.plannedStartTotal);
        op.setPlannedEndTotal(row.plannedEndTotal);
        op.setInfeasible(row.infeasible);
        return op;
    }

    public static OntOperationEntity fromOperation(
            Operation op, String workspaceId, String revisionId) {
        OntOperationEntity row = new OntOperationEntity();
        row.stampKeys(workspaceId, revisionId, op.getId());
        row.supplyOrderId = op.getSupplyOrderId();
        row.planUnitId = op.getPlanUnitId();
        row.sequenceNr = op.getSequenceNr();
        row.routingSequenceNo = op.getRoutingSequenceNo();
        row.operationName = op.getOperationName();
        row.productionDuration = op.getProductionDuration();
        row.preprocessingTime = op.getPreprocessingTime();
        row.postprocessingTime = op.getPostprocessingTime();
        row.segmentIndex = op.getSegmentIndex();
        row.lastSegment = op.isLastSegment();
        row.parallelGroupId = op.getParallelGroupId();
        row.locked = op.isLocked();
        row.earliestPossibleStartOwn = op.getEarliestPossibleStartOwn();
        row.earliestPossibleEndOwn = op.getEarliestPossibleEndOwn();
        row.earliestPossibleStartTotal = op.getEarliestPossibleStartTotal();
        row.earliestPossibleEndTotal = op.getEarliestPossibleEndTotal();
        row.latestDesiredStart = op.getLatestDesiredStart();
        row.latestDesiredEnd = op.getLatestDesiredEnd();
        row.plannedStartTotal = op.getPlannedStartTotal();
        row.plannedEndTotal = op.getPlannedEndTotal();
        row.infeasible = op.isInfeasible();
        return row;
    }

    public static Fulfillment toFulfillment(OntFulfillmentEntity row) {
        return new Fulfillment(
                row.entityId,
                row.demandId,
                row.supplyId,
                row.quantity,
                FulfillmentType.valueOf(row.type));
    }

    public static OntFulfillmentEntity fromFulfillment(
            Fulfillment ff, String workspaceId, String revisionId) {
        OntFulfillmentEntity row = new OntFulfillmentEntity();
        row.stampKeys(workspaceId, revisionId, ff.getId());
        row.demandId = ff.getDemandId();
        row.supplyId = ff.getSupplyId();
        row.quantity = ff.getQuantity();
        row.type = ff.getType().name();
        return row;
    }

    public static ProductInStockingPointPeriod toPispp(OntPisppEntity row) {
        ProductInStockingPointPeriod p = new ProductInStockingPointPeriod(
                row.entityId, row.pispId, row.periodId);
        p.setOnHand(row.onHand);
        p.setPlannedSupplyTotal(row.plannedSupplyTotal);
        p.setPlannedSupplyTotalMrp(row.plannedSupplyTotalMrp);
        p.setPlannedSupplyTotalOptimized(row.plannedSupplyTotalOptimized);
        p.setPlannedDemandQuantityTotal(row.plannedDemandQuantityTotal);
        p.setInventoryTargetQuantity(row.inventoryTargetQuantity);
        return p;
    }

    public static OntPisppEntity fromPispp(
            ProductInStockingPointPeriod p, String workspaceId, String revisionId) {
        OntPisppEntity row = new OntPisppEntity();
        row.stampKeys(workspaceId, revisionId, p.getId());
        row.pispId = p.getPispId();
        row.periodId = p.getPeriodId();
        row.onHand = p.getOnHand();
        row.plannedSupplyTotal = p.getPlannedSupplyTotal();
        row.plannedSupplyTotalMrp = p.getPlannedSupplyTotalMrp();
        row.plannedSupplyTotalOptimized = p.getPlannedSupplyTotalOptimized();
        row.plannedDemandQuantityTotal = p.getPlannedDemandQuantityTotal();
        row.inventoryTargetQuantity = p.getInventoryTargetQuantity();
        row.plannedInventoryLevel = p.getPlannedInventoryLevel();
        row.replenishedInventoryLevel = p.getReplenishedInventoryLevel();
        row.stockShortageQuantity = p.getStockShortageQuantity();
        return row;
    }

    public static Period toPeriod(OntPeriodEntity row) {
        Period period = new Period(row.entityId, row.sequenceNr, row.startDate, row.endDate);
        period.setGranularity(PeriodGranularity.valueOf(row.granularity));
        period.setShiftId(row.shiftId);
        period.setParentPeriodId(row.parentPeriodId);
        period.setStartDateTime(row.startDateTime);
        period.setEndDateTime(row.endDateTime);
        period.setLeaf(row.leaf);
        return period;
    }

    public static OntPeriodEntity fromPeriod(Period period, String workspaceId, String revisionId) {
        OntPeriodEntity row = new OntPeriodEntity();
        row.stampKeys(workspaceId, revisionId, period.getId());
        row.sequenceNr = period.getSequenceNr();
        row.startDate = period.getStartDate();
        row.endDate = period.getEndDate();
        row.granularity = period.getGranularity().name();
        row.shiftId = period.getShiftId();
        row.parentPeriodId = period.getParentPeriodId();
        row.startDateTime = period.getStartDateTime();
        row.endDateTime = period.getEndDateTime();
        row.leaf = period.isLeaf();
        return row;
    }

    public static StandardResourcePeriod toSrp(OntSrpEntity row) {
        StandardResourcePeriod srp = new StandardResourcePeriod(
                row.entityId, row.standardResourceId, row.periodId);
        srp.setTotalCapacity(row.totalCapacity);
        srp.setCalendarDowntime(row.calendarDowntime);
        srp.setTechnicalDowntime(row.technicalDowntime);
        srp.setReservedCapacity(row.reservedCapacity);
        return srp;
    }

    public static PhysicalResourcePeriod toPrp(OntPrpEntity row) {
        PhysicalResourcePeriod prp = new PhysicalResourcePeriod(
                row.entityId, row.physicalResourceId, row.standardResourceId, row.periodId);
        prp.setTotalCapacity(row.totalCapacity);
        prp.setCalendarDowntime(row.calendarDowntime);
        prp.setSchedulerFeedbackMinutes(row.schedulerFeedbackMinutes);
        prp.setReservedCapacity(row.reservedCapacity);
        return prp;
    }

    public static OntPrpEntity fromPrp(
            PhysicalResourcePeriod prp, String workspaceId, String revisionId) {
        OntPrpEntity row = new OntPrpEntity();
        row.stampKeys(workspaceId, revisionId, prp.getId());
        row.physicalResourceId = prp.getPhysicalResourceId();
        row.standardResourceId = prp.getStandardResourceId();
        row.periodId = prp.getPeriodId();
        row.totalCapacity = prp.getTotalCapacity();
        row.calendarDowntime = prp.getCalendarDowntime();
        row.schedulerFeedbackMinutes = prp.getSchedulerFeedbackMinutes();
        row.reservedCapacity = prp.getReservedCapacity();
        row.availableCapacity = prp.getAvailableCapacity();
        row.overloadCapacity = prp.getOverloadCapacity();
        return row;
    }

    public static OntSrpEntity fromSrp(
            StandardResourcePeriod srp, String workspaceId, String revisionId) {
        OntSrpEntity row = new OntSrpEntity();
        row.stampKeys(workspaceId, revisionId, srp.getId());
        row.standardResourceId = srp.getStandardResourceId();
        row.periodId = srp.getPeriodId();
        row.totalCapacity = srp.getTotalCapacity();
        row.calendarDowntime = srp.getCalendarDowntime();
        row.technicalDowntime = srp.getTechnicalDowntime();
        row.reservedCapacity = srp.getReservedCapacity();
        row.availableCapacity = srp.getAvailableCapacity();
        row.freeCapacity = srp.getFreeCapacity();
        row.overloadCapacity = srp.getOverloadCapacity();
        return row;
    }

    public static ResourceCapacityAssignment toResourceCapacityAssignment(
            OntResourceCapacityAssignmentEntity row) {
        return new ResourceCapacityAssignment(
                row.entityId,
                row.operationId,
                row.operationOnStandardResourceId,
                row.standardResourcePeriodId,
                row.assignedMinutes,
                row.operationTotalMinutes,
                row.locked,
                row.parallelGroupId);
    }

    public static OntResourceCapacityAssignmentEntity fromResourceCapacityAssignment(
            ResourceCapacityAssignment rca, String workspaceId, String revisionId) {
        OntResourceCapacityAssignmentEntity row = new OntResourceCapacityAssignmentEntity();
        row.stampKeys(workspaceId, revisionId, rca.getId());
        row.operationId = rca.getOperationId();
        row.operationOnStandardResourceId = rca.getOperationOnStandardResourceId();
        row.standardResourcePeriodId = rca.getStandardResourcePeriodId();
        row.assignedMinutes = rca.getAssignedMinutes();
        row.operationTotalMinutes = rca.getOperationTotalMinutes();
        row.locked = rca.isLocked();
        row.parallelGroupId = rca.getParallelGroupId();
        return row;
    }
}
