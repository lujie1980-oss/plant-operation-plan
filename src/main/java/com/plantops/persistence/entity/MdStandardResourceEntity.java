package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.3 md_standard_resource */
@Entity
@Table(name = "md_standard_resource", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "code"
}))
public class MdStandardResourceEntity extends WorkspaceScopedEntity {

    @Column(length = 128)
    public String code;

    @Column(length = 256)
    public String name;

    @Column(name = "resource_group_code", length = 128)
    public String resourceGroupCode;

    @Column(name = "capacity_uom", length = 32)
    public String capacityUom;

    @Column(name = "is_bottleneck")
    public boolean bottleneck;

    @Column(name = "resource_efficiency")
    public BigDecimal resourceEfficiency;

    public static List<MdStandardResourceEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
