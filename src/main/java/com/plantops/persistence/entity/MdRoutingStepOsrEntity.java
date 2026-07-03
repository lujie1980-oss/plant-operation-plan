package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.2 md_routing_step_osr */
@Entity
@Table(name = "md_routing_step_osr", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "routing_code", "sequence_no", "standard_resource_code"
}))
public class MdRoutingStepOsrEntity extends WorkspaceScopedEntity {

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

    public static List<MdRoutingStepOsrEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
