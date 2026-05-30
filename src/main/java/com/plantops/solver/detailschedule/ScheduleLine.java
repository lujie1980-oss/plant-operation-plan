package com.plantops.solver.detailschedule;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

public class ScheduleLine {

    @PlanningId
    private String lineId;
    private String resourceId;
    private String areaId;
    private boolean opened;
    private int capacityMinutes;

    public ScheduleLine() {
    }

    public ScheduleLine(String lineId, String resourceId, String areaId, boolean opened, int capacityMinutes) {
        this.lineId = lineId;
        this.resourceId = resourceId;
        this.areaId = areaId;
        this.opened = opened;
        this.capacityMinutes = capacityMinutes;
    }

    public String getLineId() {
        return lineId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getAreaId() {
        return areaId;
    }

    public boolean isOpened() {
        return opened;
    }

    public int getCapacityMinutes() {
        return capacityMinutes;
    }
}
