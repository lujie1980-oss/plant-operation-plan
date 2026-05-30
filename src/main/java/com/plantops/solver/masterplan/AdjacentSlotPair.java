package com.plantops.solver.masterplan;

/**
 * 同一资源上 index 相邻的两个时间槽，供产能均衡软约束使用。
 */
public record AdjacentSlotPair(TimeSlot earlier, TimeSlot later) {
}
