package com.plantops.scenario;

import com.plantops.masterdata.MasterDataExtensionService;
import com.plantops.persistence.entity.ProductResourceEntity;

/**
 * 从工艺 BOM（{@link ProductResourceEntity}）解析换型矩阵所需的工艺属性。
 * <p>
 * 线材、关键物料、公母端、分支等字段来自工艺 BOM 导入列，与换型矩阵「属性」列对齐。
 */
public final class ChangeoverProductAttributeResolver {

    private ChangeoverProductAttributeResolver() {
    }

    /**
     * 解析料号在指定工序上的换型属性值；无匹配行时回退到该料号首条工艺行。
     */
    public static String resolve(String productCode, String operationName, String attributeKey) {
        if (productCode == null || productCode.isBlank()) {
            return ChangeoverAttributeKey.wildcard();
        }
        String key = ChangeoverAttributeKey.normalizeCode(attributeKey);
        if (ChangeoverAttributeKey.PRODUCT_CODE.code().equals(key)) {
            return ChangeoverAttributeKey.normalizeValue(productCode);
        }
        ProductResourceEntity row = ProductResourceEntity.findByProductAndOperation(productCode, operationName);
        if (row == null) {
            row = ProductResourceEntity.findFirstByProduct(productCode);
        }
        if (row == null) {
            return ChangeoverAttributeKey.wildcard();
        }
        String resolved = MasterDataExtensionService.resolveProductResourceAttribute(row, key);
        if (resolved != null && !resolved.isBlank()) {
            return ChangeoverAttributeKey.normalizeValue(resolved);
        }
        return ChangeoverAttributeKey.wildcard();
    }
}
