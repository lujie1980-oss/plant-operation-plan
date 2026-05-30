package com.plantops.scenario;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 换型矩阵属性键：与 product_resource 字段及 Excel 列「属性」对齐。 */
public enum ChangeoverAttributeKey {
    WIRE_MATERIAL("wireMaterial", "线材"),
    KEY_MATERIAL("keyMaterial", "关键物料"),
    TOTAL_BRANCH("totalBranch", "分支"),
    PRODUCT_CODE("productCode", "料号");

    private static final Map<String, ChangeoverAttributeKey> BY_CODE = Map.of(
            "wireMaterial", WIRE_MATERIAL,
            "keyMaterial", KEY_MATERIAL,
            "totalBranch", TOTAL_BRANCH,
            "productCode", PRODUCT_CODE);

    private final String code;
    private final String label;

    ChangeoverAttributeKey(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static Optional<ChangeoverAttributeKey> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        ChangeoverAttributeKey byCode = BY_CODE.get(trimmed);
        if (byCode != null) {
            return Optional.of(byCode);
        }
        for (ChangeoverAttributeKey key : values()) {
            if (key.label.equals(trimmed) || key.code.equalsIgnoreCase(trimmed)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    public static String normalizeCode(String raw) {
        return parse(raw).map(ChangeoverAttributeKey::code).orElse(raw.trim());
    }

    public static String displayLabel(String code) {
        if (code == null) {
            return "";
        }
        for (ChangeoverAttributeKey key : values()) {
            if (key.code.equals(code)) {
                return key.label;
            }
        }
        return code;
    }

    public static String wildcard() {
        return "*";
    }

    public static boolean isWildcard(String value) {
        return value == null || value.isBlank() || "*".equals(value.trim());
    }

    public static String normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            return wildcard();
        }
        return value.trim();
    }
}
