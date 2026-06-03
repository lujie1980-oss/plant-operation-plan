package com.plantops.masterdata;

import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 主数据 Custom 字段：extensions JSON 与 legacy 列的双读双写。
 */
public final class MasterDataExtensionService {

    public static final List<String> PRODUCT_RESOURCE_CUSTOM_KEYS = List.of(
            "bomLevel",
            "wireMaterial",
            "keyMaterial",
            "maleFemaleEnd",
            "totalBranch",
            "standardLabor");

    private MasterDataExtensionService() {
    }

    public static Map<String, Object> readProductResourceExtensions(ProductResourceEntity entity) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (entity.extensions != null) {
            merged.putAll(entity.extensions);
        }
        for (String key : PRODUCT_RESOURCE_CUSTOM_KEYS) {
            merged.putIfAbsent(key, readLegacyProductResourceValue(entity, key));
        }
        return stripNulls(merged);
    }

    public static void applyProductResourceCustomFields(
            ProductResourceEntity entity,
            Map<String, Object> extensions,
            String bomLevel,
            String wireMaterial,
            String keyMaterial,
            String maleFemaleEnd,
            String totalBranch,
            BigDecimal standardLabor) {
        Map<String, Object> values = new LinkedHashMap<>();
        putIfPresent(values, "bomLevel", bomLevel);
        putIfPresent(values, "wireMaterial", wireMaterial);
        putIfPresent(values, "keyMaterial", keyMaterial);
        putIfPresent(values, "maleFemaleEnd", maleFemaleEnd);
        putIfPresent(values, "totalBranch", totalBranch);
        putIfPresent(values, "standardLabor", standardLabor);
        if (extensions != null) {
            values.putAll(extensions);
        }

        Map<String, Object> customOnly = new LinkedHashMap<>();
        for (String key : PRODUCT_RESOURCE_CUSTOM_KEYS) {
            if (values.containsKey(key)) {
                customOnly.put(key, normalizeStoredValue(values.get(key)));
            }
        }
        entity.extensions = customOnly.isEmpty() ? null : customOnly;
        syncLegacyProductResourceColumns(entity, customOnly);
    }

    public static void backfillProductResourceExtensions(ProductResourceEntity entity) {
        Map<String, Object> merged = readProductResourceExtensions(entity);
        if (merged.isEmpty()) {
            entity.extensions = null;
            return;
        }
        entity.extensions = new LinkedHashMap<>(merged);
        syncLegacyProductResourceColumns(entity, merged);
    }

    public static String resolveProductResourceAttribute(ProductResourceEntity entity, String fieldKey) {
        if (entity == null || fieldKey == null || fieldKey.isBlank()) {
            return null;
        }
        Map<String, Object> extensions = readProductResourceExtensions(entity);
        Object value = extensions.get(fieldKey);
        if (value == null) {
            value = readLegacyProductResourceValue(entity, fieldKey);
        }
        return value == null ? null : String.valueOf(value);
    }

    public static Map<String, Object> readMaterialExtensions(MaterialEntity entity) {
        if (entity.extensions == null || entity.extensions.isEmpty()) {
            return Map.of();
        }
        return stripNulls(new LinkedHashMap<>(entity.extensions));
    }

    public static Map<String, Object> mergeExtensionMaps(
            Map<String, Object> existing,
            Map<String, Object> updates) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        if (updates != null) {
            merged.putAll(updates);
        }
        return stripNulls(merged);
    }

    public static Object parseExtensionCell(String raw, String dataType) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String type = dataType == null || dataType.isBlank() ? "STRING" : dataType.trim().toUpperCase();
        return switch (type) {
            case "INTEGER" -> Integer.parseInt(raw.trim().split("\\.")[0]);
            case "NUMBER" -> new BigDecimal(raw.trim());
            case "BOOL", "BOOLEAN" -> parseBool(raw);
            case "DATE" -> LocalDate.parse(raw.trim());
            default -> raw.trim();
        };
    }

    private static boolean parseBool(String raw) {
        String s = raw.trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("是") || s.equals("yes") || s.equals("y");
    }

    public static void applyMaterialExtensions(MaterialEntity entity, Map<String, Object> extensions) {
        if (extensions == null) {
            return;
        }
        if (extensions.isEmpty()) {
            entity.extensions = null;
            return;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : extensions.entrySet()) {
            Object normalized = normalizeStoredValue(entry.getValue());
            if (normalized != null) {
                cleaned.put(entry.getKey(), normalized);
            }
        }
        entity.extensions = cleaned.isEmpty() ? null : cleaned;
    }

    public static String resolveMaterialAttribute(MaterialEntity entity, String fieldKey) {
        if (entity == null || fieldKey == null || fieldKey.isBlank()) {
            return null;
        }
        Object value = readMaterialExtensions(entity).get(fieldKey);
        return value == null ? null : String.valueOf(value);
    }

    private static void syncLegacyProductResourceColumns(ProductResourceEntity entity, Map<String, Object> values) {
        entity.bomLevel = asString(values.get("bomLevel"));
        entity.wireMaterial = asString(values.get("wireMaterial"));
        entity.keyMaterial = asString(values.get("keyMaterial"));
        entity.maleFemaleEnd = asString(values.get("maleFemaleEnd"));
        entity.totalBranch = asString(values.get("totalBranch"));
        entity.standardLabor = asBigDecimal(values.get("standardLabor"));
    }

    private static Object readLegacyProductResourceValue(ProductResourceEntity entity, String key) {
        Object raw = switch (key) {
            case "bomLevel" -> entity.bomLevel;
            case "wireMaterial" -> entity.wireMaterial;
            case "keyMaterial" -> entity.keyMaterial;
            case "maleFemaleEnd" -> entity.maleFemaleEnd;
            case "totalBranch" -> entity.totalBranch;
            case "standardLabor" -> entity.standardLabor;
            default -> null;
        };
        if (raw instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return raw;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static Map<String, Object> stripNulls(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() != null && !Objects.equals(entry.getValue(), "")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Object normalizeStoredValue(Object value) {
        if (value instanceof String s) {
            return s.isBlank() ? null : s.trim();
        }
        if (value instanceof Number n && !(value instanceof BigDecimal)) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return value;
    }

    public static String stringValue(Object value) {
        return asString(value);
    }

    public static BigDecimal decimalValue(Object value) {
        return asBigDecimal(value);
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }
}
