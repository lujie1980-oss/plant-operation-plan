package com.plantops.ontology.supply;

import java.time.LocalDateTime;

public class Operation {

    private String id;
    private String supplyOrderId;
    private String planUnitId;
    private int sequenceNr;
    /** 工艺路线 sequenceNo（与 OrderAllocation.operationSeq / allocationId 对齐）。 */
    private int routingSequenceNo;
    private String operationName;
    /** 纯加工占用（秒）：quantity × processTimeSeconds（主 OOSR），不含换型。 */
    private long productionDuration;
    /** 开工前准备（秒）：含换型 setup；不占槽位产能，但计入日历跨度。 */
    private long preprocessingTime;
    /** 完工后缓冲（秒）：末道可含后处理规则；中间道后续可接流转规则。 */
    private long postprocessingTime;
    private int segmentIndex;
    private boolean lastSegment;
    private String parallelGroupId;
    private boolean locked;

    /** 不考虑上游工序：本工序自身最早可开工。 */
    private LocalDateTime earliestPossibleStartOwn;
    /** 不考虑上游工序：本工序自身最早可完工（= startOwn + elapsed）。 */
    private LocalDateTime earliestPossibleEndOwn;
    /** 考虑上游工序串行制约：本工序最早可开工。 */
    private LocalDateTime earliestPossibleStartTotal;
    /** 考虑上游工序串行制约：本工序最早可完工。 */
    private LocalDateTime earliestPossibleEndTotal;
    /** JIT 倒排：本工序最晚要求开工。 */
    private LocalDateTime latestDesiredStart;
    /** JIT 倒排：本工序最晚要求完工。 */
    private LocalDateTime latestDesiredEnd;
    /** 计划开工（求解/确认后写入；推导时清空）。 */
    private LocalDateTime plannedStartTotal;
    /** 计划完工（求解/确认后写入；推导时清空）。 */
    private LocalDateTime plannedEndTotal;
    private boolean infeasible;

    public Operation() {
    }

    public Operation(String id, String supplyOrderId, int sequenceNr, String operationName) {
        this.id = id;
        this.supplyOrderId = supplyOrderId;
        this.sequenceNr = sequenceNr;
        this.operationName = operationName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSupplyOrderId() {
        return supplyOrderId;
    }

    public void setSupplyOrderId(String supplyOrderId) {
        this.supplyOrderId = supplyOrderId;
    }

    public String getPlanUnitId() {
        return planUnitId;
    }

    public void setPlanUnitId(String planUnitId) {
        this.planUnitId = planUnitId;
    }

    public int getSequenceNr() {
        return sequenceNr;
    }

    public void setSequenceNr(int sequenceNr) {
        this.sequenceNr = sequenceNr;
    }

    public int getRoutingSequenceNo() {
        return routingSequenceNo;
    }

    public void setRoutingSequenceNo(int routingSequenceNo) {
        this.routingSequenceNo = routingSequenceNo;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public long getProductionDuration() {
        return productionDuration;
    }

    public void setProductionDuration(long productionDuration) {
        this.productionDuration = Math.max(0, productionDuration);
    }

    public long getPreprocessingTime() {
        return preprocessingTime;
    }

    public void setPreprocessingTime(long preprocessingTime) {
        this.preprocessingTime = Math.max(0, preprocessingTime);
    }

    public long getPostprocessingTime() {
        return postprocessingTime;
    }

    public void setPostprocessingTime(long postprocessingTime) {
        this.postprocessingTime = Math.max(0, postprocessingTime);
    }

    /** 日历跨度（秒）：前处理 + 生产 + 后处理。 */
    public long totalElapsedSeconds() {
        return preprocessingTime + productionDuration + postprocessingTime;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(int segmentIndex) {
        this.segmentIndex = Math.max(0, segmentIndex);
    }

    public boolean isLastSegment() {
        return lastSegment;
    }

    public void setLastSegment(boolean lastSegment) {
        this.lastSegment = lastSegment;
    }

    public String getParallelGroupId() {
        return parallelGroupId;
    }

    public void setParallelGroupId(String parallelGroupId) {
        this.parallelGroupId = parallelGroupId;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public LocalDateTime getEarliestPossibleStartOwn() {
        return earliestPossibleStartOwn;
    }

    public void setEarliestPossibleStartOwn(LocalDateTime earliestPossibleStartOwn) {
        this.earliestPossibleStartOwn = earliestPossibleStartOwn;
    }

    public LocalDateTime getEarliestPossibleEndOwn() {
        return earliestPossibleEndOwn;
    }

    public void setEarliestPossibleEndOwn(LocalDateTime earliestPossibleEndOwn) {
        this.earliestPossibleEndOwn = earliestPossibleEndOwn;
    }

    public LocalDateTime getEarliestPossibleStartTotal() {
        return earliestPossibleStartTotal;
    }

    public void setEarliestPossibleStartTotal(LocalDateTime earliestPossibleStartTotal) {
        this.earliestPossibleStartTotal = earliestPossibleStartTotal;
    }

    public LocalDateTime getEarliestPossibleEndTotal() {
        return earliestPossibleEndTotal;
    }

    public void setEarliestPossibleEndTotal(LocalDateTime earliestPossibleEndTotal) {
        this.earliestPossibleEndTotal = earliestPossibleEndTotal;
    }

    public LocalDateTime getLatestDesiredStart() {
        return latestDesiredStart;
    }

    public void setLatestDesiredStart(LocalDateTime latestDesiredStart) {
        this.latestDesiredStart = latestDesiredStart;
    }

    public LocalDateTime getLatestDesiredEnd() {
        return latestDesiredEnd;
    }

    public void setLatestDesiredEnd(LocalDateTime latestDesiredEnd) {
        this.latestDesiredEnd = latestDesiredEnd;
    }

    public LocalDateTime getPlannedStartTotal() {
        return plannedStartTotal;
    }

    public void setPlannedStartTotal(LocalDateTime plannedStartTotal) {
        this.plannedStartTotal = plannedStartTotal;
    }

    public LocalDateTime getPlannedEndTotal() {
        return plannedEndTotal;
    }

    public void setPlannedEndTotal(LocalDateTime plannedEndTotal) {
        this.plannedEndTotal = plannedEndTotal;
    }

    public boolean isInfeasible() {
        return infeasible;
    }

    public void setInfeasible(boolean infeasible) {
        this.infeasible = infeasible;
    }

    public void clearPlannedTimes() {
        plannedStartTotal = null;
        plannedEndTotal = null;
    }
}
