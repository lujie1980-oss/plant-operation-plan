package com.plantops.solver.masterplan;

/**
 * 排程反馈已冻结占用：指定时间槽上不可再分配的产能（分钟）。
 */
public class SlotFixedLoad {

    private final String slotId;
    private final int fixedMinutes;

    public SlotFixedLoad(String slotId, int fixedMinutes) {
        this.slotId = slotId;
        this.fixedMinutes = Math.max(0, fixedMinutes);
    }

    public String getSlotId() {
        return slotId;
    }

    public int getFixedMinutes() {
        return fixedMinutes;
    }
}
