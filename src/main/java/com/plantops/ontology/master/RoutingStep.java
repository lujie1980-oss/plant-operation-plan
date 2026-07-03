package com.plantops.ontology.master;

/**
 * 工艺路线工序（主数据层，对应 {@code ProductResourceEntity} 同 sequence 分组）。
 */
public class RoutingStep {

    private String id;
    private String routingId;
    private int sequenceNo;
    private String operationName;

    public RoutingStep() {
    }

    public RoutingStep(String id, String routingId, int sequenceNo, String operationName) {
        this.id = id;
        this.routingId = routingId;
        this.sequenceNo = sequenceNo;
        this.operationName = operationName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoutingId() {
        return routingId;
    }

    public void setRoutingId(String routingId) {
        this.routingId = routingId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }
}
