package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.List;

/** §11.3.1 md_stocking_point */
@Entity
@Table(name = "md_stocking_point", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "code"
}))
public class MdStockingPointEntity extends WorkspaceScopedEntity {

    @Column(length = 128)
    public String code;

    @Column(length = 256)
    public String name;

    @Column(name = "site_code", length = 64)
    public String siteCode;

    public static List<MdStockingPointEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
