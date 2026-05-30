package com.plantops.scenario.planning;

/**
 * 物料推演共享上下文：S04 / S05 在同一流水线运行中共用同一 {@link InventorySnapshot}。
 */
public final class MaterialPlanningContext {

    private final InventorySnapshot inventory;

    public MaterialPlanningContext(InventorySnapshot inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory snapshot is required");
        }
        this.inventory = inventory;
    }

    public InventorySnapshot inventory() {
        return inventory;
    }

    public String inventorySnapshotId() {
        return inventory.snapshotId();
    }
}
