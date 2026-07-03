package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.3 external_resource_group → md_resource_group */
@Entity
@Table(name = "external_resource_group")
public class ExternalResourceGroupEntity extends ExternalStagingEntity {

    @Column(name = "resource_group_code", length = 128)
    public String resourceGroupCode;

    @Column(name = "resource_group_name", length = 256)
    public String resourceGroupName;

    @Column(name = "calendar_code", length = 64)
    public String calendarCode;

    @Column(name = "resource_efficiency")
    public BigDecimal resourceEfficiency;

    public static List<ExternalResourceGroupEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalResourceGroupEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
