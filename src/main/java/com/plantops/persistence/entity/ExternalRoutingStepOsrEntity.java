package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.2 external_routing_step_on_standard_resource → md_routing_step_osr */
@Entity
@Table(name = "external_routing_step_on_standard_resource")
public class ExternalRoutingStepOsrEntity extends ExternalStagingEntity {

    @Column(name = "routing_code", length = 128)
    public String routingCode;

    @Column(name = "sequence_no")
    public int sequenceNo;

    @Column(name = "standard_resource_code", length = 128)
    public String standardResourceCode;

    @Column(name = "resource_priority")
    public int resourcePriority;

    @Column(name = "setup_time_minutes")
    public int setupTimeMinutes;

    @Column(name = "process_time_seconds")
    public BigDecimal processTimeSeconds;

    @Column(name = "process_time_uom", length = 32)
    public String processTimeUom;

    @Column(name = "production_rate")
    public BigDecimal productionRate;

    @Column(name = "resource_usage_type", length = 32)
    public String resourceUsageType;

    @Column(name = "batch_size")
    public BigDecimal batchSize;

    @Column(name = "batch_duration_minutes")
    public int batchDurationMinutes;

    public static List<ExternalRoutingStepOsrEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalRoutingStepOsrEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
