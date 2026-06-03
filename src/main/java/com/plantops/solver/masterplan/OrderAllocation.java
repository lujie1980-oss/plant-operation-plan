package com.plantops.solver.masterplan;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@PlanningEntity
public class OrderAllocation {

    @PlanningId
    private String id;
    private String workOrderNo;
    private String parentWorkOrderNo;
    private String salesOrderNo;
    private int salesOrderLineNo;
    private String productCode;
    private String resourceId;
    private String operationName;
    private int operationSeq;
    private LocalDate dueDate;
    private int priority;
    private int durationMinutes;
    /** 同一工单拆段序号，从 0 起 */
    private int segmentIndex;
    /** 拆段时标记：该段是否为工单的最后一道工序段 */
    private boolean lastSegment;
    private BigDecimal workOrderQuantity;
    private boolean locked;

    /** 并行工序组（同组须同槽开工）；与 S05 pairGroupId 语义对应 */
    private String parallelGroupId;

    /** 并行规则仅匹配一头：可排到工艺路线内其它资源槽位 */
    private boolean parallelOrphan;

    /** 并行规则指定的产线（孤儿/配对追溯） */
    private String designatedLineId;

    private List<String> allowedResourceIds = new ArrayList<>();

    /** 构建问题时按 resourceId 填入，仅含本机台槽位 */
    private List<TimeSlot> eligibleTimeSlots = new ArrayList<>();

    @ValueRangeProvider(id = "eligibleTimeSlots")
    public List<TimeSlot> getEligibleTimeSlots() {
        return eligibleTimeSlots;
    }

    public void setEligibleTimeSlots(List<TimeSlot> eligibleTimeSlots) {
        this.eligibleTimeSlots = eligibleTimeSlots != null ? eligibleTimeSlots : new ArrayList<>();
    }

    @PlanningVariable(valueRangeProviderRefs = "eligibleTimeSlots")
    private TimeSlot timeSlot;

    public OrderAllocation() {
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

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public int getOperationSeq() {
        return operationSeq;
    }

    public void setOperationSeq(int operationSeq) {
        this.operationSeq = operationSeq;
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

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(int segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public boolean isLastSegment() {
        return lastSegment;
    }

    public void setLastSegment(boolean lastSegment) {
        this.lastSegment = lastSegment;
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

    public boolean isParallelOrphan() {
        return parallelOrphan;
    }

    public void setParallelOrphan(boolean parallelOrphan) {
        this.parallelOrphan = parallelOrphan;
    }

    public String getDesignatedLineId() {
        return designatedLineId;
    }

    public void setDesignatedLineId(String designatedLineId) {
        this.designatedLineId = designatedLineId;
    }

    public List<String> getAllowedResourceIds() {
        return allowedResourceIds;
    }

    public void setAllowedResourceIds(List<String> allowedResourceIds) {
        this.allowedResourceIds = allowedResourceIds != null ? new ArrayList<>(allowedResourceIds) : new ArrayList<>();
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(TimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }
}
