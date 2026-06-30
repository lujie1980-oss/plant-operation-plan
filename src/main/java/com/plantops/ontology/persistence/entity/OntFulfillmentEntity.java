package com.plantops.ontology.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "ont_fulfillment")
public class OntFulfillmentEntity extends OntRevisionScopedEntity {

    @Column(name = "demand_id", nullable = false, length = 128)
    public String demandId;

    @Column(name = "supply_id", nullable = false, length = 128)
    public String supplyId;

    @Column(nullable = false)
    public double quantity;

    @Column(nullable = false, length = 32)
    public String type;

    public static List<OntFulfillmentEntity> forRevision(String workspaceId, String revisionId) {
        return list("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
    }
}
