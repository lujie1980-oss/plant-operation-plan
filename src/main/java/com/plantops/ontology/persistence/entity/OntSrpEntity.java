package com.plantops.ontology.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "ont_srp")
public class OntSrpEntity extends OntRevisionScopedEntity {

    @Column(name = "standard_resource_id", nullable = false, length = 128)
    public String standardResourceId;

    @Column(name = "period_id", nullable = false, length = 128)
    public String periodId;

    @Column(name = "total_capacity", nullable = false)
    public double totalCapacity;

    @Column(name = "calendar_downtime", nullable = false)
    public double calendarDowntime;

    @Column(name = "technical_downtime", nullable = false)
    public double technicalDowntime;

    @Column(name = "reserved_capacity", nullable = false)
    public double reservedCapacity;

    @Column(name = "available_capacity", nullable = false)
    public double availableCapacity;

    @Column(name = "free_capacity", nullable = false)
    public double freeCapacity;

    @Column(name = "overload_capacity", nullable = false)
    public double overloadCapacity;

    public static List<OntSrpEntity> forRevision(String workspaceId, String revisionId) {
        return list("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
    }
}
