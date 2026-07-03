package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

/** §11.3.3 external_physical_resource → md_physical_resource */
@Entity
@Table(name = "external_physical_resource")
public class ExternalPhysicalResourceEntity extends ExternalStagingEntity {

    @Column(name = "physical_resource_code", length = 128)
    public String physicalResourceCode;

    @Column(name = "physical_resource_name", length = 256)
    public String physicalResourceName;

    @Column(name = "standard_resource_code", length = 128)
    public String standardResourceCode;

    @Column(name = "production_line_code", length = 128)
    public String productionLineCode;

    @Column(name = "status", length = 32)
    public String status;

    public static List<ExternalPhysicalResourceEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalPhysicalResourceEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
