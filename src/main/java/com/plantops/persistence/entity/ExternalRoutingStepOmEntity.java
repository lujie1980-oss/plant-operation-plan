package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.2 external_routing_step_output_material → md_routing_step_om */
@Entity
@Table(name = "external_routing_step_output_material")
public class ExternalRoutingStepOmEntity extends ExternalStagingEntity {

    @Column(name = "routing_code", length = 128)
    public String routingCode;

    @Column(name = "sequence_no")
    public int sequenceNo;

    @Column(name = "output_product_code", length = 128)
    public String outputProductCode;

    @Column(name = "output_qty")
    public BigDecimal outputQty;

    @Column(name = "receive_stocking_point_code", length = 128)
    public String receiveStockingPointCode;

    public static List<ExternalRoutingStepOmEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalRoutingStepOmEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
