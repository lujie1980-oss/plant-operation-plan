package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.2 md_routing_step_im */
@Entity
@Table(name = "md_routing_step_im", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "routing_code", "sequence_no", "component_product_code"
}))
public class MdRoutingStepImEntity extends WorkspaceScopedEntity {

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

    public static List<MdRoutingStepImEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
