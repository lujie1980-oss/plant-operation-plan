package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.2 md_routing_step */
@Entity
@Table(name = "md_routing_step", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "routing_code", "sequence_no"
}))
public class MdRoutingStepEntity extends WorkspaceScopedEntity {

    @Column(name = "routing_code", length = 128)
    public String routingCode;

    @Column(name = "sequence_no")
    public int sequenceNo;

    @Column(name = "operation_code", length = 128)
    public String operationCode;

    @Column(name = "operation_name", length = 256)
    public String operationName;

    @Column(name = "resource_group_code", length = 128)
    public String resourceGroupCode;

    @Column(name = "yield_rate")
    public BigDecimal yieldRate;

    @Column(name = "pre_processing_minutes")
    public int preProcessingMinutes;

    @Column(name = "scheduling_space_minutes")
    public int schedulingSpaceMinutes;

    @Column(name = "production_minutes")
    public int productionMinutes;

    @Column(name = "post_processing_minutes")
    public int postProcessingMinutes;

    public static List<MdRoutingStepEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
