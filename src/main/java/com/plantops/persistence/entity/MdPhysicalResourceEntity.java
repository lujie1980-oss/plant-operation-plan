package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.List;

/** §11.3.3 md_physical_resource */
@Entity
@Table(name = "md_physical_resource", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "code"
}))
public class MdPhysicalResourceEntity extends WorkspaceScopedEntity {

    @Column(length = 128)
    public String code;

    @Column(length = 256)
    public String name;

    @Column(name = "standard_resource_code", length = 128)
    public String standardResourceCode;

    @Column(name = "production_line_code", length = 128)
    public String productionLineCode;

    @Column(length = 32)
    public String status;

    public static List<MdPhysicalResourceEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
