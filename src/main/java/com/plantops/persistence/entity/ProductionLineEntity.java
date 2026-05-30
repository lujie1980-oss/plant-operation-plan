package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



import java.util.List;



@Entity

@Table(name = "production_line", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "line_id"

}))

public class ProductionLineEntity extends WorkspaceScopedEntity {



    public String lineId;

    public String areaId;

    public String resourceId;

    public int lineMinHeadcount;

    public int lineCapacityPerShift;



    public static List<ProductionLineEntity> listInWorkspace() {

        return list("workspaceId", ws());

    }



    public static List<ProductionLineEntity> findByArea(String areaId) {

        return list("workspaceId = ?1 and areaId = ?2", ws(), areaId);

    }



    public static ProductionLineEntity findByLineId(String lineId) {

        return find("workspaceId = ?1 and lineId = ?2", ws(), lineId).firstResult();

    }

    public static List<ProductionLineEntity> findByResourceId(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return List.of();
        }
        return list("workspaceId = ?1 and resourceId = ?2 order by lineId", ws(), resourceId);
    }

}


