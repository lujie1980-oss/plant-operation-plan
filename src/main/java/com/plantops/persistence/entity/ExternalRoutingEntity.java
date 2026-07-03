package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.List;

/** §11.3.2 external_routing → md_routing */
@Entity
@Table(name = "external_routing")
public class ExternalRoutingEntity extends ExternalStagingEntity {

    @Column(name = "routing_code", length = 128)
    public String routingCode;

    @Column(name = "product_code", length = 128)
    public String productCode;

    @Column(name = "stocking_point_code", length = 128)
    public String stockingPointCode;

    @Column(name = "path_priority")
    public int pathPriority;

    @Column(name = "routing_name", length = 256)
    public String routingName;

    @Column(name = "effective_from")
    public LocalDate effectiveFrom;

    @Column(name = "effective_to")
    public LocalDate effectiveTo;

    public static List<ExternalRoutingEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalRoutingEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
