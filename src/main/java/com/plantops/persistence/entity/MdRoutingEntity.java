package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.List;

/** §11.3.2 md_routing */
@Entity
@Table(name = "md_routing", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "routing_code"
}))
public class MdRoutingEntity extends WorkspaceScopedEntity {

    @Column(name = "routing_code", length = 128)
    public String routingCode;

    @Column(name = "product_code", length = 128)
    public String productCode;

    @Column(name = "stocking_point_code", length = 128)
    public String stockingPointCode;

    @Column(name = "path_priority")
    public int pathPriority;

    @Column(length = 256)
    public String name;

    @Column(name = "effective_from")
    public LocalDate effectiveFrom;

    @Column(name = "effective_to")
    public LocalDate effectiveTo;

    public static List<MdRoutingEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
