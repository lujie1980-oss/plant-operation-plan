package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.3 external_standard_resource → md_standard_resource */
@Entity
@Table(name = "external_standard_resource")
public class ExternalStandardResourceEntity extends ExternalStagingEntity {

    @Column(name = "standard_resource_code", length = 128)
    public String standardResourceCode;

    @Column(name = "standard_resource_name", length = 256)
    public String standardResourceName;

    @Column(name = "resource_group_code", length = 128)
    public String resourceGroupCode;

    @Column(name = "capacity_uom", length = 32)
    public String capacityUom;

    @Column(name = "is_bottleneck")
    public boolean bottleneck;

    @Column(name = "resource_efficiency")
    public BigDecimal resourceEfficiency;

    public static List<ExternalStandardResourceEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalStandardResourceEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
