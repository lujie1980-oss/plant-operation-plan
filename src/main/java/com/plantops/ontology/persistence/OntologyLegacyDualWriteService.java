package com.plantops.ontology.persistence;

import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.WorkOrderSupplyOrderMapper;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * P4 migration: sync legacy {@code work_order} rows into {@code ont_supply_order} for a revision.
 */
@ApplicationScoped
public class OntologyLegacyDualWriteService {

    @Inject
    com.plantops.config.LegacySchemaSupport legacySchemaSupport;

    @Inject
    OntologyP0UpsertService upsertService;

    /**
     * Upsert one {@code ont_supply_order} per legacy work order in the current workspace.
     *
     * @return number of supply orders written
     */
    @Transactional
    public int syncSupplyOrdersFromWorkOrders(String workspaceId, String revisionId) {
        if (!legacySchemaSupport.isLegacySchemaEnabled()) {
            return 0;
        }
        List<WorkOrderEntity> workOrders = WorkOrderEntity.list("workspaceId", workspaceId);
        for (WorkOrderEntity wo : workOrders) {
            SupplyOrder mapped = WorkOrderSupplyOrderMapper.toSupplyOrder(wo);
            if (mapped != null) {
                upsertService.upsertSupplyOrder(mapped, workspaceId, revisionId);
            }
        }
        return workOrders.size();
    }
}
