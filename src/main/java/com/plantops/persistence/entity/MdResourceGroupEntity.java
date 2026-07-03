package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.3 md_resource_group */
@Entity
@Table(name = "md_resource_group", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "code"
}))
public class MdResourceGroupEntity extends WorkspaceScopedEntity {

    @Column(length = 128)
    public String code;

    @Column(length = 256)
    public String name;

    @Column(name = "calendar_code", length = 64)
    public String calendarCode;

    @Column(name = "resource_efficiency")
    public BigDecimal resourceEfficiency;

    public static List<MdResourceGroupEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
