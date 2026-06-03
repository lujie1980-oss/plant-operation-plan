package com.plantops.masterdata;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 工艺 BOM 工序名称标准化：与换型矩阵工序名（裁线/半成品/成品/标签/气密）对齐。
 */
public final class ProductResourceOperationNames {

    private static final Pattern PLACEHOLDER = Pattern.compile("^工序\\s*\\d+$");

    public static final Map<String, String> RESOURCE_TO_STANDARD = Map.ofEntries(
            Map.entry("通用裁线机", "裁线"),
            Map.entry("NET裁线设备", "裁线"),
            Map.entry("Coaxial", "半成品"),
            Map.entry("MATE-net", "半成品"),
            Map.entry("总成", "成品"),
            Map.entry("小标签设备", "标签"),
            Map.entry("气密设备", "气密"));

    public static final Map<Integer, String> SEQUENCE_TO_STANDARD = Map.of(
            1, "裁线",
            2, "半成品",
            3, "标签",
            4, "气密",
            5, "成品");

    private ProductResourceOperationNames() {
    }

    /**
     * 写入/展示用：空白或占位名「工序 N」时，按设备组、工序编号推断标准工序名。
     */
    public static String normalize(String operationName, String resourceId, Integer sequenceNo) {
        String trimmed = blankToNull(operationName);
        if (trimmed != null && !isPlaceholder(trimmed) && !equalsResourceId(trimmed, resourceId)) {
            return trimmed;
        }
        String inferred = inferFromResource(resourceId);
        if (inferred != null) {
            return inferred;
        }
        if (sequenceNo != null && sequenceNo > 0) {
            String bySeq = SEQUENCE_TO_STANDARD.get(sequenceNo);
            if (bySeq != null) {
                return bySeq;
            }
        }
        return trimmed;
    }

    public static boolean needsNormalization(String operationName, String resourceId) {
        String trimmed = blankToNull(operationName);
        if (trimmed == null) {
            return true;
        }
        return isPlaceholder(trimmed) || equalsResourceId(trimmed, resourceId);
    }

    public static String inferFromResource(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return null;
        }
        return RESOURCE_TO_STANDARD.get(resourceId.trim());
    }

    public static boolean isPlaceholder(String operationName) {
        return operationName != null && PLACEHOLDER.matcher(operationName.trim()).matches();
    }

    private static boolean equalsResourceId(String operationName, String resourceId) {
        return resourceId != null && operationName.trim().equals(resourceId.trim());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
