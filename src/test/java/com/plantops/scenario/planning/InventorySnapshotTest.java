package com.plantops.scenario.planning;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class InventorySnapshotTest {

    @Test
    void newMutablePoolIsIndependentCopy() {
        InventorySnapshot snapshot = InventorySnapshot.of("test-id", Map.of("P-1", BigDecimal.TEN));

        Map<String, BigDecimal> pool = snapshot.newMutablePool();
        pool.put("P-1", BigDecimal.ZERO);
        pool.put("P-2", BigDecimal.ONE);

        assertEquals(BigDecimal.TEN, snapshot.availableByProduct().get("P-1"));
        assertEquals(1, snapshot.productCount());
    }

    @Test
    void materialPlanningContextExposesSnapshotId() {
        InventorySnapshot snapshot = InventorySnapshot.of("abc12345", Map.of());
        MaterialPlanningContext ctx = new MaterialPlanningContext(snapshot);
        assertEquals("abc12345", ctx.inventorySnapshotId());
        assertEquals(snapshot, ctx.inventory());
    }

    @Test
    void distinctSnapshotsHaveDistinctIds() {
        InventorySnapshot a = InventorySnapshot.of("id-a", Map.of("X", BigDecimal.ONE));
        InventorySnapshot b = InventorySnapshot.of("id-b", Map.of("X", BigDecimal.ONE));
        assertNotEquals(a.snapshotId(), b.snapshotId());
    }
}
