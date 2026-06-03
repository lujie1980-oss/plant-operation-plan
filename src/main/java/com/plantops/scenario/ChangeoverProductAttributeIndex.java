package com.plantops.scenario;

import com.plantops.masterdata.MasterDataExtensionService;
import com.plantops.masterdata.ProductResourceOperationNames;
import com.plantops.persistence.entity.ProductResourceEntity;

import java.util.HashMap;
import java.util.Map;

/** 换型属性快照：求解期不访问数据库。 */
public final class ChangeoverProductAttributeIndex {

    private record AttributeKey(String productCode, String operationName, String attributeKey) {
    }

    private static final ChangeoverProductAttributeIndex EMPTY =
            new ChangeoverProductAttributeIndex(Map.of(), Map.of());

    private final Map<AttributeKey, String> exact;
    /** productCode -> attributeKey -> value（首条工艺 BOM 行兜底） */
    private final Map<String, Map<String, String>> fallbackByProduct;

    private ChangeoverProductAttributeIndex(
            Map<AttributeKey, String> exact,
            Map<String, Map<String, String>> fallbackByProduct) {
        this.exact = Map.copyOf(exact);
        this.fallbackByProduct = fallbackByProduct.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> Map.copyOf(e.getValue())));
    }

    public static ChangeoverProductAttributeIndex empty() {
        return EMPTY;
    }

    /** 单测：预置一条属性快照。 */
    static ChangeoverProductAttributeIndex testingExact(
            String productCode, String operationName, String attributeKey, String value) {
        return new ChangeoverProductAttributeIndex(
                Map.of(new AttributeKey(productCode, operationName, attributeKey), value),
                Map.of());
    }

    public static ChangeoverProductAttributeIndex fromWorkspace() {
        Map<AttributeKey, String> exact = new HashMap<>();
        Map<String, Map<String, String>> fallbackByProduct = new HashMap<>();
        for (ProductResourceEntity row : ProductResourceEntity.listInWorkspace()) {
            if (row.productCode == null || row.productCode.isBlank()) {
                continue;
            }
            String productCode = row.productCode.trim();
            String matrixOp = ProductResourceOperationNames.normalize(
                    row.operationName, row.resourceId, row.sequenceNo);
            if (matrixOp == null || matrixOp.isBlank()) {
                continue;
            }
            Map<String, String> productFallback = fallbackByProduct.computeIfAbsent(
                    productCode, k -> new HashMap<>());
            for (ChangeoverAttributeKey key : ChangeoverAttributeKey.values()) {
                if (key == ChangeoverAttributeKey.PRODUCT_CODE) {
                    continue;
                }
                String resolved = MasterDataExtensionService.resolveProductResourceAttribute(
                        row, key.code());
                if (resolved == null || resolved.isBlank()) {
                    continue;
                }
                String normalized = ChangeoverAttributeKey.normalizeValue(resolved);
                exact.put(new AttributeKey(productCode, matrixOp, key.code()), normalized);
                productFallback.putIfAbsent(key.code(), normalized);
            }
        }
        return new ChangeoverProductAttributeIndex(exact, fallbackByProduct);
    }

    public String resolve(String productCode, String matrixOperationName, String attributeKey) {
        if (productCode == null || productCode.isBlank()) {
            return ChangeoverAttributeKey.wildcard();
        }
        String normalizedKey = ChangeoverAttributeKey.normalizeCode(attributeKey);
        if (ChangeoverAttributeKey.PRODUCT_CODE.code().equals(normalizedKey)) {
            return ChangeoverAttributeKey.normalizeValue(productCode);
        }
        String product = productCode.trim();
        String operation = matrixOperationName != null ? matrixOperationName.trim() : "";
        String value = exact.get(new AttributeKey(product, operation, normalizedKey));
        if (value != null) {
            return value;
        }
        Map<String, String> fallback = fallbackByProduct.get(product);
        if (fallback != null) {
            String fallbackValue = fallback.get(normalizedKey);
            if (fallbackValue != null) {
                return fallbackValue;
            }
        }
        return ChangeoverAttributeKey.wildcard();
    }
}
