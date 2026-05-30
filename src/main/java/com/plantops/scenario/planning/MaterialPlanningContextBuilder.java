package com.plantops.scenario.planning;

import jakarta.enterprise.context.ApplicationScoped;

/** 构建 S04/S05 共用的物料推演快照（一次读取库存主数据）。 */
@ApplicationScoped
public class MaterialPlanningContextBuilder {

    public MaterialPlanningContext build() {
        return new MaterialPlanningContext(InventorySnapshot.loadFromWorkspace());
    }
}
