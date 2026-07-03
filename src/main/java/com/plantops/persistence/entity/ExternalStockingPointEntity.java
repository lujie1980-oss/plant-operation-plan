package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

/** §11.3.1 external_stocking_point → md_stocking_point */
@Entity
@Table(name = "external_stocking_point")
public class ExternalStockingPointEntity extends ExternalStagingEntity {

    @Column(name = "stocking_point_code", length = 128)
    public String stockingPointCode;

    @Column(name = "stocking_point_name", length = 256)
    public String stockingPointName;

    @Column(name = "site_code", length = 64)
    public String siteCode;

    public static List<ExternalStockingPointEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalStockingPointEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
