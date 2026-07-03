package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.2 external_routing_step_input_material → md_routing_step_im */
@Entity
@Table(name = "external_routing_step_input_material")
public class ExternalRoutingStepImEntity extends ExternalStagingEntity {

    @Column(name = "routing_code", length = 128)
    public String routingCode;

    @Column(name = "sequence_no")
    public int sequenceNo;

    @Column(name = "component_product_code", length = 128)
    public String componentProductCode;

    @Column(name = "component_qty")
    public BigDecimal componentQty;

    @Column(name = "component_uom", length = 32)
    public String componentUom;

    @Column(name = "issue_stocking_point_code", length = 128)
    public String issueStockingPointCode;

    public static List<ExternalRoutingStepImEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalRoutingStepImEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
