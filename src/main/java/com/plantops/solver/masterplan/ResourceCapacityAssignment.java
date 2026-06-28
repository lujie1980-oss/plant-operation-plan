package com.plantops.solver.masterplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工序在某机台规划槽上的产能占用（分钟）。
 * 同一 {@link #operationKey} 可有多条（多机台并行拆分、跨天拆段），
 * Σ {@link #assignedMinutes} = {@link #operationTotalMinutes}。
 */
public class ResourceCapacityAssignment {

    private String id;
    private String workOrderNo;
    private String operationId;
    private int operationSeq;
    /** workOrderNo + "@" + operationSeq，工序聚合键 */
    private String operationKey;
    private int daySegmentIndex;

    private String resourceId;
    private int resourcePriority;
    private String productCode;
    private String operationName;

    private int operationTotalMinutes;
    private int slotCapacityMinutes;

    private String parentWorkOrderNo;
    private String salesOrderNo;
    private int salesOrderLineNo;
    private LocalDate dueDate;
    private int priority;
    private BigDecimal workOrderQuantity;
    private boolean locked;
    private String parallelGroupId;

    /** 本体 JIT：工序级最晚要求完工（倒排锚点）；DB 路径可为 null。 */
    private LocalDateTime operationLatestDesiredEnd;
    /** 本体 JIT：工序级最晚要求开工。 */
    private LocalDateTime operationLatestDesiredStart;

    private List<TimeSlot> eligibleTimeSlots = new ArrayList<>();

    private TimeSlot timeSlot;
    private int assignedMinutes;

    public ResourceCapacityAssignment() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkOrderNo() {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo) {
        this.workOrderNo = workOrderNo;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public int getOperationSeq() {
        return operationSeq;
    }

    public void setOperationSeq(int operationSeq) {
        this.operationSeq = operationSeq;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }

    public int getDaySegmentIndex() {
        return daySegmentIndex;
    }

    public void setDaySegmentIndex(int daySegmentIndex) {
        this.daySegmentIndex = daySegmentIndex;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public int getResourcePriority() {
        return resourcePriority;
    }

    public void setResourcePriority(int resourcePriority) {
        this.resourcePriority = resourcePriority;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public int getOperationTotalMinutes() {
        return operationTotalMinutes;
    }

    public void setOperationTotalMinutes(int operationTotalMinutes) {
        this.operationTotalMinutes = operationTotalMinutes;
    }

    public int getSlotCapacityMinutes() {
        return slotCapacityMinutes;
    }

    public void setSlotCapacityMinutes(int slotCapacityMinutes) {
        this.slotCapacityMinutes = slotCapacityMinutes;
    }

    public String getParentWorkOrderNo() {
        return parentWorkOrderNo;
    }

    public void setParentWorkOrderNo(String parentWorkOrderNo) {
        this.parentWorkOrderNo = parentWorkOrderNo;
    }

    public String getSalesOrderNo() {
        return salesOrderNo;
    }

    public void setSalesOrderNo(String salesOrderNo) {
        this.salesOrderNo = salesOrderNo;
    }

    public int getSalesOrderLineNo() {
        return salesOrderLineNo;
    }

    public void setSalesOrderLineNo(int salesOrderLineNo) {
        this.salesOrderLineNo = salesOrderLineNo;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public BigDecimal getWorkOrderQuantity() {
        return workOrderQuantity;
    }

    public void setWorkOrderQuantity(BigDecimal workOrderQuantity) {
        this.workOrderQuantity = workOrderQuantity;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getParallelGroupId() {
        return parallelGroupId;
    }

    public void setParallelGroupId(String parallelGroupId) {
        this.parallelGroupId = parallelGroupId;
    }

    public LocalDateTime getOperationLatestDesiredEnd() {
        return operationLatestDesiredEnd;
    }

    public void setOperationLatestDesiredEnd(LocalDateTime operationLatestDesiredEnd) {
        this.operationLatestDesiredEnd = operationLatestDesiredEnd;
    }

    public LocalDateTime getOperationLatestDesiredStart() {
        return operationLatestDesiredStart;
    }

    public void setOperationLatestDesiredStart(LocalDateTime operationLatestDesiredStart) {
        this.operationLatestDesiredStart = operationLatestDesiredStart;
    }

    public List<TimeSlot> getEligibleTimeSlots() {
        return eligibleTimeSlots;
    }

    public void setEligibleTimeSlots(List<TimeSlot> eligibleTimeSlots) {
        this.eligibleTimeSlots = eligibleTimeSlots != null ? eligibleTimeSlots : new ArrayList<>();
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(TimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }

    public int getAssignedMinutes() {
        return assignedMinutes;
    }

    public void setAssignedMinutes(int assignedMinutes) {
        this.assignedMinutes = assignedMinutes;
    }

    public static String operationKey(String workOrderNo, int operationSeq) {
        return workOrderNo + "@" + operationSeq;
    }

    public static String allocationId(String workOrderNo, int operationSeq, String resourceId, int daySegmentIndex) {
        return workOrderNo + "@OP" + operationSeq + "@" + resourceId + "#D" + daySegmentIndex;
    }
}
