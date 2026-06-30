package com.plantops.ontology.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "ont_supply_order")
public class OntSupplyOrderEntity extends OntRevisionScopedEntity {

    @Column(name = "product_code", nullable = false, length = 64)
    public String productCode;

    @Column(name = "pisp_id", nullable = false, length = 128)
    public String pispId;

    @Column(nullable = false)
    public double quantity;

    @Column(name = "need_date", nullable = false)
    public LocalDate needDate;

    @Column(nullable = false, length = 32)
    public String status;

    @Column(nullable = false, length = 32)
    public String type;

    public static List<OntSupplyOrderEntity> forRevision(String workspaceId, String revisionId) {
        return list("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
    }
}
