package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.2 external_routing_step → md_routing_step */
@Entity
@Table(name = "external_routing_step")
public class ExternalRoutingStepEntity extends ExternalStagingEntity {

    @Column(name = "routing_code", length = 128)
    public String routingCode;

    @Column(name = "sequence_no")
    public int sequenceNo;

    @Column(name = "operation_code", length = 128)
    public String operationCode;

    @Column(name = "operation_name", length = 256)
    public String operationName;

    @Column(name = "standard_resource_group_code", length = 128)
    public String standardResourceGroupCode;

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

    public static List<ExternalRoutingStepEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalRoutingStepEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
