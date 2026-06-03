package com.plantops.scenario;

/** 相邻工序衔接模式（生产规则 · 工序流转时间）。 */
public enum OperationLinkMode {
    /** 标准顺序：后道在前道结束后，间隔落在 [min, max] 内。 */
    STANDARD,
    /** 同时开始：前后工序 start 相同。 */
    SIMULTANEOUS_START,
    /** 延后开始：后道 start >= 前道 start + delayStartMinutes。 */
    DELAYED_START,
    /** 同时结束：前后工序 end 相同。 */
    SIMULTANEOUS_END;

    public static OperationLinkMode fromDb(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        try {
            return OperationLinkMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return STANDARD;
        }
    }
}
