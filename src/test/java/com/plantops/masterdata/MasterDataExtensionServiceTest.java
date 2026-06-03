package com.plantops.masterdata;

import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MasterDataExtensionServiceTest {

    @Test
    void applyAndRead_customFieldsRoundTrip() {
        ProductResourceEntity entity = new ProductResourceEntity();
        MasterDataExtensionService.applyProductResourceCustomFields(
                entity,
                Map.of("wireMaterial", "W1", "standardLabor", 2.5),
                null,
                "legacy-wire",
                "KM1",
                null,
                null,
                null);

        Map<String, Object> extensions = MasterDataExtensionService.readProductResourceExtensions(entity);
        assertEquals("W1", extensions.get("wireMaterial"));
        assertEquals("KM1", extensions.get("keyMaterial"));
        assertEquals(new BigDecimal("2.5"), extensions.get("standardLabor"));
        assertEquals("W1", entity.wireMaterial);
        assertEquals("KM1", entity.keyMaterial);
    }

    @Test
    void backfill_fromLegacyColumns() {
        ProductResourceEntity entity = new ProductResourceEntity();
        entity.wireMaterial = " copper ";
        entity.totalBranch = "2分支";
        entity.extensions = null;

        MasterDataExtensionService.backfillProductResourceExtensions(entity);

        assertEquals("copper", entity.extensions.get("wireMaterial"));
        assertEquals("2分支", entity.extensions.get("totalBranch"));
    }

    @Test
    void resolveAttribute_prefersExtensions() {
        ProductResourceEntity entity = new ProductResourceEntity();
        entity.wireMaterial = "legacy";
        entity.extensions = Map.of("wireMaterial", "json");

        assertEquals("json", MasterDataExtensionService.resolveProductResourceAttribute(entity, "wireMaterial"));
    }

    @Test
    void stringValue_blankToNull() {
        assertNull(MasterDataExtensionService.stringValue("  "));
        assertEquals("x", MasterDataExtensionService.stringValue(" x "));
    }

    @Test
    void materialExtensions_roundTrip() {
        MaterialEntity entity = new MaterialEntity();
        MasterDataExtensionService.applyMaterialExtensions(
                entity,
                Map.of("harnessFamily", "HF-01", "leadTimeBuffer", 3));

        Map<String, Object> read = MasterDataExtensionService.readMaterialExtensions(entity);
        assertEquals("HF-01", read.get("harnessFamily"));
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) read.get("leadTimeBuffer")));
        assertEquals("HF-01", MasterDataExtensionService.resolveMaterialAttribute(entity, "harnessFamily"));
    }

    @Test
    void applyMaterialExtensions_nullPreservesExisting() {
        MaterialEntity entity = new MaterialEntity();
        entity.extensions = Map.of("harnessFamily", "HF-01");
        MasterDataExtensionService.applyMaterialExtensions(entity, null);
        assertEquals("HF-01", entity.extensions.get("harnessFamily"));
    }

    @Test
    void mergeExtensionMaps_overlaysUpdatesAndStripsNulls() {
        Map<String, Object> updates = new java.util.LinkedHashMap<>();
        updates.put("b", "new");
        updates.put("c", null);
        Map<String, Object> merged = MasterDataExtensionService.mergeExtensionMaps(
                Map.of("a", 1, "b", "old"),
                updates);
        assertEquals(1, merged.get("a"));
        assertEquals("new", merged.get("b"));
        assertNull(merged.get("c"));
    }

    @Test
    void parseExtensionCell_coercesTypes() {
        assertEquals(3, MasterDataExtensionService.parseExtensionCell("3.0", "INTEGER"));
        assertEquals(new BigDecimal("2.5"), MasterDataExtensionService.parseExtensionCell("2.5", "NUMBER"));
        assertEquals(true, MasterDataExtensionService.parseExtensionCell("是", "BOOL"));
        assertNull(MasterDataExtensionService.parseExtensionCell("  ", "STRING"));
    }
}
