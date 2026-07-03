package com.plantops.api.dto.planning;

public record SimulateMasterPlanSessionRequest(
        String targetType,
        String targetId,
        String pispPeriodId,
        String property,
        Double value,
        String dateValue) {

    /** 兼容 M1–M3：仅 pispPeriodId + property + value。 */
    public SimulateMasterPlanSessionRequest(String pispPeriodId, String property, Double value) {
        this(null, null, pispPeriodId, property, value, null);
    }

    public OntologySimulateTargetType effectiveTargetType() {
        return OntologySimulateTargetType.parse(targetType);
    }

    public String effectiveTargetId() {
        if (targetId != null && !targetId.isBlank()) {
            return targetId;
        }
        return pispPeriodId;
    }
}
