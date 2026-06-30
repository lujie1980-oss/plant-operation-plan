package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.persistence.entity.OntDemandEntity;
import com.plantops.ontology.persistence.entity.OntEntityKey;
import com.plantops.ontology.persistence.entity.OntFulfillmentEntity;
import com.plantops.ontology.persistence.entity.OntOperationEntity;
import com.plantops.ontology.persistence.entity.OntPisppEntity;
import com.plantops.ontology.persistence.entity.OntSrpEntity;
import com.plantops.ontology.persistence.entity.OntSupplyOrderEntity;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Upsert P0 ont_* rows for a DRAFT revision (§5.14.3 · TODO-12 P2). */
@ApplicationScoped
public class OntologyP0UpsertService {

    @Inject
    OntologyEntityPolicyService policyService;

    public void replaceP0Graph(String workspaceId, String revisionId, OntologyGraph graph) {
        replaceP0Graph(workspaceId, revisionId, graph, OntologyEntityPolicyService.PERSISTENCE_FULL);
    }

    public void replaceP0Graph(
            String workspaceId, String revisionId, OntologyGraph graph, String persistenceMode) {
        deleteP0Entities(workspaceId, revisionId, persistenceMode);
        upsertP0Graph(workspaceId, revisionId, graph, persistenceMode);
    }

    public void upsertP0Graph(String workspaceId, String revisionId, OntologyGraph graph) {
        upsertP0Graph(workspaceId, revisionId, graph, OntologyEntityPolicyService.PERSISTENCE_FULL);
    }

    public void upsertP0Graph(
            String workspaceId, String revisionId, OntologyGraph graph, String persistenceMode) {
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.DEMAND)) {
            graph.demandsById().values().forEach(d -> upsertDemand(d, workspaceId, revisionId));
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.SUPPLY_ORDER)) {
            graph.supplyOrdersById().values().forEach(so -> upsertSupplyOrder(so, workspaceId, revisionId));
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.OPERATION)) {
            graph.operationsById().values().forEach(op -> upsertOperation(op, workspaceId, revisionId));
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.FULFILLMENT)) {
            upsertFulfillments(workspaceId, revisionId, graph);
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.PISPP)) {
            graph.pispPeriodsById().values().forEach(p -> upsertPispp(p, workspaceId, revisionId));
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.SRP)) {
            graph.srpById().values().forEach(srp -> upsertSrp(srp, workspaceId, revisionId));
        }
    }

    private void upsertFulfillments(String workspaceId, String revisionId, OntologyGraph graph) {
        Set<String> desiredIds = new HashSet<>();
        for (Fulfillment ff : graph.fulfillments()) {
            desiredIds.add(ff.getId());
            upsertFulfillment(ff, workspaceId, revisionId);
        }
        for (OntFulfillmentEntity row : OntFulfillmentEntity.forRevision(workspaceId, revisionId)) {
            if (!desiredIds.contains(row.entityId)) {
                row.delete();
            }
        }
    }

    private void upsertDemand(Demand d, String workspaceId, String revisionId) {
        OntDemandEntity row = OntDemandEntity.findById(new OntEntityKey(workspaceId, revisionId, d.getId()));
        if (row == null) {
            OntologyEntityMapper.fromDemand(d, workspaceId, revisionId).persist();
            return;
        }
        row.productCode = d.getProductCode();
        row.pispId = d.getPispId();
        row.quantity = d.getQuantity();
        row.needDate = d.getNeedDate();
        row.priority = d.getPriority();
        row.sourceType = d.getSourceType().name();
        row.sourceId = d.getSourceId();
        row.updatedAt = LocalDateTime.now();
    }

    public void upsertSupplyOrder(SupplyOrder so, String workspaceId, String revisionId) {
        OntSupplyOrderEntity row = OntSupplyOrderEntity.findById(
                new OntEntityKey(workspaceId, revisionId, so.getId()));
        if (row == null) {
            OntologyEntityMapper.fromSupplyOrder(so, workspaceId, revisionId).persist();
            return;
        }
        row.productCode = so.getProductCode();
        row.pispId = so.getPispId();
        row.quantity = so.getQuantity();
        row.needDate = so.getNeedDate();
        row.status = so.getStatus().name();
        row.type = so.getType().name();
        row.updatedAt = LocalDateTime.now();
    }

    private void upsertOperation(Operation op, String workspaceId, String revisionId) {
        OntOperationEntity row = OntOperationEntity.findById(
                new OntEntityKey(workspaceId, revisionId, op.getId()));
        if (row == null) {
            OntologyEntityMapper.fromOperation(op, workspaceId, revisionId).persist();
            return;
        }
        copyOperationFields(row, op);
        row.updatedAt = LocalDateTime.now();
    }

    private void upsertFulfillment(Fulfillment ff, String workspaceId, String revisionId) {
        OntFulfillmentEntity row = OntFulfillmentEntity.findById(
                new OntEntityKey(workspaceId, revisionId, ff.getId()));
        if (row == null) {
            OntologyEntityMapper.fromFulfillment(ff, workspaceId, revisionId).persist();
            return;
        }
        row.demandId = ff.getDemandId();
        row.supplyId = ff.getSupplyId();
        row.quantity = ff.getQuantity();
        row.type = ff.getType().name();
        row.updatedAt = LocalDateTime.now();
    }

    private void upsertPispp(ProductInStockingPointPeriod p, String workspaceId, String revisionId) {
        OntPisppEntity row = OntPisppEntity.findById(
                new OntEntityKey(workspaceId, revisionId, p.getId()));
        if (row == null) {
            OntologyEntityMapper.fromPispp(p, workspaceId, revisionId).persist();
            return;
        }
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
        row.updatedAt = LocalDateTime.now();
    }

    private void upsertSrp(StandardResourcePeriod srp, String workspaceId, String revisionId) {
        OntSrpEntity row = OntSrpEntity.findById(
                new OntEntityKey(workspaceId, revisionId, srp.getId()));
        if (row == null) {
            OntologyEntityMapper.fromSrp(srp, workspaceId, revisionId).persist();
            return;
        }
        row.standardResourceId = srp.getStandardResourceId();
        row.periodId = srp.getPeriodId();
        row.totalCapacity = srp.getTotalCapacity();
        row.calendarDowntime = srp.getCalendarDowntime();
        row.technicalDowntime = srp.getTechnicalDowntime();
        row.reservedCapacity = srp.getReservedCapacity();
        row.availableCapacity = srp.getAvailableCapacity();
        row.freeCapacity = srp.getFreeCapacity();
        row.overloadCapacity = srp.getOverloadCapacity();
        row.updatedAt = LocalDateTime.now();
    }

    static void copyOperationFields(OntOperationEntity row, Operation op) {
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
    }

    public void deleteP0Entities(String workspaceId, String revisionId) {
        deleteP0Entities(workspaceId, revisionId, OntologyEntityPolicyService.PERSISTENCE_FULL);
    }

    public void deleteP0Entities(String workspaceId, String revisionId, String persistenceMode) {
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.DEMAND)) {
            OntDemandEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.SUPPLY_ORDER)) {
            OntSupplyOrderEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.OPERATION)) {
            OntOperationEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.FULFILLMENT)) {
            OntFulfillmentEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.PISPP)) {
            OntPisppEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.SRP)) {
            OntSrpEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        }
    }
}
