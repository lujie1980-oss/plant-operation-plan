package com.plantops.api.dto.planning;

public record SrpSnapshotDto(
        String id,
        String resourceId,
        String periodId,
        double totalCapacity,
        double calendarDowntime,
        double reservedCapacity,
        double availableCapacity,
        double freeCapacity,
        double overloadCapacity) {
}
