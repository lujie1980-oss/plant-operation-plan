package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



import java.math.BigDecimal;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Set;



@Entity

@Table(name = "production_resource", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "resource_id"

}))

public class ProductionResourceEntity extends WorkspaceScopedEntity {



    public String resourceId;

    public String resourceGroup;

    public String areaId;

    public boolean bottleneck;

    public BigDecimal runRatePerHour = BigDecimal.ONE;



    public static List<ProductionResourceEntity> listInWorkspace() {

        return list("workspaceId", ws());

    }



    public static ProductionResourceEntity findByResourceId(String resourceId) {

        return find("workspaceId = ?1 and resourceId = ?2", ws(), resourceId).firstResult();

    }



    public static List<ProductionResourceEntity> findBottlenecks() {

        return list("workspaceId = ?1 and bottleneck = true", ws());

    }



    /** 瓶颈 + 产品工艺路线涉及的资源（主计划 / 产能平衡共用） */

    public static Set<String> routingResourceIds() {

        Set<String> ids = new LinkedHashSet<>();

        for (ProductionResourceEntity r : findBottlenecks()) {

            ids.add(r.resourceId);

        }

        for (ProductResourceEntity pr : ProductResourceEntity.listInWorkspace()) {

            ids.add(pr.resourceId);

        }

        return ids;

    }

}


