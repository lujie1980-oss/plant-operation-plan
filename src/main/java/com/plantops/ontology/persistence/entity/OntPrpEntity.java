package com.plantops.ontology.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "ont_physical_resource_period")
public class OntPrpEntity extends OntRevisionScopedEntity {

    @Column(name = "physical_resource_id", nullable = false, length = 128)
    public String physicalResourceId;

    @Column(name = "standard_resource_id", nullable = false, length = 128)
    public String standardResourceId;

    @Column(name = "period_id", nullable = false, length = 128)
    public String periodId;

    @Column(name = "total_capacity", nullable = false)
    public double totalCapacity;

    @Column(name = "calendar_downtime", nullable = false)
    public double calendarDowntime;

    @Column(name = "scheduler_feedback_minutes", nullable = false)
    public double schedulerFeedbackMinutes;

    @Column(name = "reserved_capacity", nullable = false)
    public double reservedCapacity;

    @Column(name = "available_capacity", nullable = false)
    public double availableCapacity;

    @Column(name = "overload_capacity", nullable = false)
    public double overloadCapacity;

    public static List<OntPrpEntity> forRevision(String workspaceId, String revisionId) {
        return list("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
    }
}
