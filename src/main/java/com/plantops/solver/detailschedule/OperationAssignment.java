package com.plantops.solver.detailschedule;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.NextElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@PlanningEntity
public class OperationAssignment {

    @PlanningId
    private String operationId;
    private String workOrderNo;
    private String batchNo;
    private java.math.BigDecimal batchQuantity;
    private String productCode;
    private String resourceId;
    private String operationName;
    private int durationMinutes;
    private LocalDate dueDate;
    private boolean kittingEligible;
    /** 相对排程锚点的最早可开工分钟（未齐套时由 kitting_lock_t_hours 推导）。 */
    private int earliestStartMinute;
    private boolean pinned;
    private int sequenceHint;
    private int operationSeq;
    /** 工单末道工序：用于 L1 交期（完成日 vs dueDate） */
    private boolean lastOperationForDueDate;
    /** 主计划目标完成日（L2） */
    private LocalDate mpTargetEndDate;
    /** 主计划契约窗口（资源级）：开始/结束日 + 目标资源。 */
    private LocalDate mpContractStartDate;
    private LocalDate mpContractEndDate;
    private String mpContractResourceId;

    /** 并行工序配对组 ID（同组须同产线、同起同止） */
    private String pairGroupId;
    private String pairMateOperationId;
    /** 规则指定的产线 ID（线体，如 YD-13） */
    private String designatedLineId;
    private boolean parallelPaired;
    private boolean parallelOrphan;
    /** 孤儿工序可排产的产线 ID 列表 */
    private List<String> allowedLineIds;

    /** 同工序多资源：按 resourcePriority 排序的可选设备组 */
    private List<String> allowedResourceIds = new ArrayList<>();

    /** 连续生产组 ID（同组在同产线上须连续排产，不得插入其它料号） */
    private String continuousGroupId;
    private boolean continuousProduction;

    /** 工艺链上前道工序（固定引用，非规划变量）。 */
    private OperationAssignment routingPredecessor;

    /** 所属产线（由 {@link ScheduleLine#assignedOperations} 逆推）。 */
    @InverseRelationShadowVariable(sourceVariableName = "assignedOperations")
    private ScheduleLine line;

    @PreviousElementShadowVariable(sourceVariableName = "assignedOperations")
    private OperationAssignment previousOnLine;

    @NextElementShadowVariable(sourceVariableName = "assignedOperations")
    private OperationAssignment nextOnLine;

    @ShadowVariable(supplierName = "startMinuteSupplier")
    private Integer startMinute;

    public OperationAssignment() {
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getWorkOrderNo() {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo) {
        this.workOrderNo = workOrderNo;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public java.math.BigDecimal getBatchQuantity() {
        return batchQuantity;
    }

    public void setBatchQuantity(java.math.BigDecimal batchQuantity) {
        this.batchQuantity = batchQuantity;
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

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isKittingEligible() {
        return kittingEligible;
    }

    public void setKittingEligible(boolean kittingEligible) {
        this.kittingEligible = kittingEligible;
    }

    public int getEarliestStartMinute() {
        return earliestStartMinute;
    }

    public void setEarliestStartMinute(int earliestStartMinute) {
        this.earliestStartMinute = Math.max(0, earliestStartMinute);
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public int getSequenceHint() {
        return sequenceHint;
    }

    public void setSequenceHint(int sequenceHint) {
        this.sequenceHint = sequenceHint;
    }

    public int getOperationSeq() {
        return operationSeq;
    }

    public void setOperationSeq(int operationSeq) {
        this.operationSeq = operationSeq;
    }

    public boolean isLastOperationForDueDate() {
        return lastOperationForDueDate;
    }

    public void setLastOperationForDueDate(boolean lastOperationForDueDate) {
        this.lastOperationForDueDate = lastOperationForDueDate;
    }

    public LocalDate getMpTargetEndDate() {
        return mpTargetEndDate;
    }

    public void setMpTargetEndDate(LocalDate mpTargetEndDate) {
        this.mpTargetEndDate = mpTargetEndDate;
    }

    public LocalDate getMpContractStartDate() {
        return mpContractStartDate;
    }

    public void setMpContractStartDate(LocalDate mpContractStartDate) {
        this.mpContractStartDate = mpContractStartDate;
    }

    public LocalDate getMpContractEndDate() {
        return mpContractEndDate;
    }

    public void setMpContractEndDate(LocalDate mpContractEndDate) {
        this.mpContractEndDate = mpContractEndDate;
    }

    public String getMpContractResourceId() {
        return mpContractResourceId;
    }

    public void setMpContractResourceId(String mpContractResourceId) {
        this.mpContractResourceId = mpContractResourceId;
    }

    public String getPairGroupId() {
        return pairGroupId;
    }

    public void setPairGroupId(String pairGroupId) {
        this.pairGroupId = pairGroupId;
    }

    public String getPairMateOperationId() {
        return pairMateOperationId;
    }

    public void setPairMateOperationId(String pairMateOperationId) {
        this.pairMateOperationId = pairMateOperationId;
    }

    public String getDesignatedLineId() {
        return designatedLineId;
    }

    public void setDesignatedLineId(String designatedLineId) {
        this.designatedLineId = designatedLineId;
    }

    public boolean isParallelPaired() {
        return parallelPaired;
    }

    public void setParallelPaired(boolean parallelPaired) {
        this.parallelPaired = parallelPaired;
    }

    public boolean isParallelOrphan() {
        return parallelOrphan;
    }

    public void setParallelOrphan(boolean parallelOrphan) {
        this.parallelOrphan = parallelOrphan;
    }

    public List<String> getAllowedLineIds() {
        return allowedLineIds;
    }

    public void setAllowedLineIds(List<String> allowedLineIds) {
        this.allowedLineIds = allowedLineIds;
    }

    public List<String> getAllowedResourceIds() {
        return allowedResourceIds;
    }

    public void setAllowedResourceIds(List<String> allowedResourceIds) {
        this.allowedResourceIds = allowedResourceIds != null ? new ArrayList<>(allowedResourceIds) : new ArrayList<>();
    }

    public String getContinuousGroupId() {
        return continuousGroupId;
    }

    public void setContinuousGroupId(String continuousGroupId) {
        this.continuousGroupId = continuousGroupId;
    }

    public boolean isContinuousProduction() {
        return continuousProduction;
    }

    public void setContinuousProduction(boolean continuousProduction) {
        this.continuousProduction = continuousProduction;
    }

    public boolean acceptsLine(ScheduleLine scheduleLine) {
        if (scheduleLine == null || scheduleLine.getLineId() == null) {
            return false;
        }
        String lineResource = scheduleLine.getResourceId();
        if (lineResource == null) {
            return false;
        }
        if (continuousProduction && designatedLineId != null) {
            return designatedLineId.equals(scheduleLine.getLineId()) && resourceAllowed(lineResource);
        }
        if (parallelPaired && designatedLineId != null) {
            return designatedLineId.equals(scheduleLine.getLineId()) && resourceAllowed(lineResource);
        }
        if (parallelOrphan) {
            boolean lineOk = allowedLineIds == null || allowedLineIds.isEmpty()
                    || allowedLineIds.contains(scheduleLine.getLineId());
            return lineOk && resourceAllowed(lineResource);
        }
        return resourceAllowed(lineResource);
    }

    private boolean resourceAllowed(String lineResource) {
        if (allowedResourceIds != null && !allowedResourceIds.isEmpty()) {
            return allowedResourceIds.contains(lineResource);
        }
        return resourceId != null && resourceId.equals(lineResource);
    }

    /** @deprecated 使用 {@link #acceptsLine(ScheduleLine)} */
    @Deprecated
    public boolean acceptsLineResource(String lineResourceId) {
        if (lineResourceId == null) {
            return false;
        }
        return resourceId != null && resourceId.equals(lineResourceId);
    }

    public OperationAssignment getRoutingPredecessor() {
        return routingPredecessor;
    }

    public void setRoutingPredecessor(OperationAssignment routingPredecessor) {
        this.routingPredecessor = routingPredecessor;
    }

    @ShadowSources({
            "previousOnLine.startMinute",
            "routingPredecessor.startMinute",
            "routingPredecessor.line",
            "line"})
    public Integer startMinuteSupplier(DetailSchedule schedule) {
        return OperationStartTimeCalculator.compute(this, schedule);
    }

    public ScheduleLine getLine() {
        return line;
    }

    public void setLine(ScheduleLine line) {
        this.line = line;
    }

    public OperationAssignment getPreviousOnLine() {
        return previousOnLine;
    }

    public void setPreviousOnLine(OperationAssignment previousOnLine) {
        this.previousOnLine = previousOnLine;
    }

    public OperationAssignment getNextOnLine() {
        return nextOnLine;
    }

    public void setNextOnLine(OperationAssignment nextOnLine) {
        this.nextOnLine = nextOnLine;
    }

    public Integer getStartMinute() {
        return startMinute;
    }

    public void setStartMinute(Integer startMinute) {
        this.startMinute = startMinute;
    }

    public Integer getEndMinute() {
        if (startMinute == null) {
            return null;
        }
        return startMinute + durationMinutes;
    }
}
