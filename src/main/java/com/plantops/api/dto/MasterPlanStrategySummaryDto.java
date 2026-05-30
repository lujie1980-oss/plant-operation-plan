package com.plantops.api.dto;

import java.util.List;

public record MasterPlanStrategySummaryDto(
        String id,
        String name,
        String capacityStrategy,
        boolean isDefault) {
}
