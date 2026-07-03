package com.plantops.ontology.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "ont_resource_capacity_assignment")
public class OntResourceCapacityAssignmentEntity extends OntRevisionScopedEntity {

    @Column(name = "operation_id", nullable = false, length = 128)
    public String operationId;

    @Column(name = "operation_on_standard_resource_id", length = 128)
    public String operationOnStandardResourceId;

    @Column(name = "standard_resource_period_id", nullable = false, length = 128)
    public String standardResourcePeriodId;

    @Column(name = "assigned_minutes", nullable = false)
    public int assignedMinutes;

    @Column(name = "operation_total_minutes", nullable = false)
    public int operationTotalMinutes;

    @Column(nullable = false)
    public boolean locked;

    @Column(name = "parallel_group_id", length = 128)
    public String parallelGroupId;

    public static List<OntResourceCapacityAssignmentEntity> forRevision(
            String workspaceId, String revisionId) {
        return list("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
    }
}
