package com.plantops.scenario.planning;

import com.plantops.persistence.entity.InventoryEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 工作区可用库存的不可变快照（期初库存池）。
 * S04 MRP 闭合与 S05 齐套推演共用同一实例，保证推演一致性。
 */
public final class InventorySnapshot {

    private final String snapshotId;
    private final LocalDateTime computedAt;
    private final Map<String, BigDecimal> availableByProduct;

    private InventorySnapshot(String snapshotId, LocalDateTime computedAt, Map<String, BigDecimal> availableByProduct) {
        this.snapshotId = snapshotId;
        this.computedAt = computedAt;
        this.availableByProduct = Map.copyOf(availableByProduct);
    }

    /** 测试 / 流水线复用同一快照实例时使用。 */
    static InventorySnapshot of(String snapshotId, Map<String, BigDecimal> availableByProduct) {
        return new InventorySnapshot(snapshotId, LocalDateTime.now(), availableByProduct);
    }

    public static InventorySnapshot loadFromWorkspace() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (InventoryEntity inv : InventoryEntity.listInWorkspace()) {
            map.merge(inv.productCode, inv.availableQty(), BigDecimal::add);
        }
        return new InventorySnapshot(
                UUID.randomUUID().toString().substring(0, 8),
                LocalDateTime.now(),
                map);
    }

    public String snapshotId() {
        return snapshotId;
    }

    public LocalDateTime computedAt() {
        return computedAt;
    }

    public Map<String, BigDecimal> availableByProduct() {
        return availableByProduct;
    }

    public int productCount() {
        return availableByProduct.size();
    }

    /** S05 齐套顺序消耗用的可变副本；不影响快照本身。 */
    public Map<String, BigDecimal> newMutablePool() {
        return new HashMap<>(availableByProduct);
    }
}
