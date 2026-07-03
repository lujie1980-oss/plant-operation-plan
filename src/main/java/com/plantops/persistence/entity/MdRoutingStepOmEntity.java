package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.2 md_routing_step_om */
@Entity
@Table(name = "md_routing_step_om", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "routing_code", "sequence_no", "output_product_code"
}))
public class MdRoutingStepOmEntity extends WorkspaceScopedEntity {

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

    public static List<MdRoutingStepOmEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
