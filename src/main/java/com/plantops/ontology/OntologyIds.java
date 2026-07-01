package com.plantops.ontology;

import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.master.StockingPoint;

public final class OntologyIds {

    public static final String DEFAULT_FG = "DEFAULT-FG";
    public static final int DEFAULT_PERIOD_COUNT = 28;

    private OntologyIds() {
    }

    public static String pispId(String productCode) {
        return pispId(productCode, StockingPoint.FG);
    }

    public static String pispId(String productCode, String stockingPointId) {
        return "PISP-" + productCode + "-" + stockingPointId;
    }

    public static String periodId(int sequenceNr) {
        return "P-" + sequenceNr;
    }

    public static String pisppId(String pispId, int sequenceNr) {
        return "PISPP-" + pispId + "-" + periodId(sequenceNr);
    }

    public static String srpId(String resourceId, int sequenceNr) {
        return "SRP-" + resourceId + "-" + periodId(sequenceNr);
    }

    public static String operationId(String supplyOrderId, int sequenceNr) {
        return "OP-" + supplyOrderId + "-" + sequenceNr;
    }

    public static String operationOnStandardResourceId(String operationId, String standardResourceId) {
        return "OOSR-" + operationId + "-" + standardResourceId;
    }

    public static String resourceCapacityAssignmentId(
            String operationId, String operationOnStandardResourceId, String standardResourcePeriodId) {
        return "RCA-" + operationId + "-" + operationOnStandardResourceId + "-" + standardResourcePeriodId;
    }

    public static String customerOrderLineId(String salesOrderNo, int salesOrderLineNo) {
        return "COL-" + salesOrderNo + "-" + salesOrderLineNo;
    }

    public static String customerOrderLineDeliveryId(String salesOrderNo, int salesOrderLineNo, int deliverySeq) {
        return "COLD-" + salesOrderNo + "-" + salesOrderLineNo + "-" + deliverySeq;
    }

    public static CustomerOrderLineDeliveryKey parseCustomerOrderLineDeliveryId(String deliveryId) {
        if (deliveryId == null || !deliveryId.startsWith("COLD-")) {
            return null;
        }
        String rest = deliveryId.substring("COLD-".length());
        int lastDash = rest.lastIndexOf('-');
        if (lastDash <= 0) {
            return null;
        }
        int deliverySeq = Integer.parseInt(rest.substring(lastDash + 1));
        rest = rest.substring(0, lastDash);
        int lineDash = rest.lastIndexOf('-');
        if (lineDash <= 0) {
            return null;
        }
        int salesOrderLineNo = Integer.parseInt(rest.substring(lineDash + 1));
        String salesOrderNo = rest.substring(0, lineDash);
        return new CustomerOrderLineDeliveryKey(salesOrderNo, salesOrderLineNo, deliverySeq);
    }

    public record CustomerOrderLineDeliveryKey(String salesOrderNo, int salesOrderLineNo, int deliverySeq) {

        public String finishedProductCode(com.plantops.ontology.OntologyGraph.Builder builder) {
            String lineId = customerOrderLineId(salesOrderNo, salesOrderLineNo);
            var line = builder.customerOrderLinesById().get(lineId);
            return line != null ? line.getProductCode() : null;
        }
    }

    public static String forecastDemandId(String forecastId) {
        return "FC-" + forecastId;
    }

    public static String demandFromCustomerDeliveryId(String deliveryId) {
        return "DEM-COLD-" + deliveryId;
    }

    public static String demandFromForecastId(String forecastDemandId) {
        return "DEM-FC-" + forecastDemandId;
    }

    public static String demandFromBomId(String supplyOrderId, String componentProductCode) {
        return "DEM-BOM-" + supplyOrderId + "-" + componentProductCode;
    }

    public static String planUnitId(String supplyOrderId, int sequenceNr) {
        return "PU-" + supplyOrderId + "-" + sequenceNr;
    }

    public static String supplyId(String supplyOrderId, int sequenceNr) {
        return "SUP-" + supplyOrderId + "-" + sequenceNr;
    }

    /** 本体上游满足链按需创建的合成供应订单（不落库）。 */
    public static String upstreamSupplyOrderId(String demandId) {
        return "SO-UP-" + demandId;
    }

    public static String inventorySupplyId(String productCode) {
        return "SUP-INV-" + productCode;
    }

    public static String shortageSupplyId(String productCode) {
        return "SUP-SHORT-" + productCode;
    }

    public static String operationInputMaterialId(String operationId, String demandId) {
        return "OIM-" + operationId + "-" + demandId;
    }

    public static String operationOutputMaterialId(String operationId, String supplyId) {
        return "OOM-" + operationId + "-" + supplyId;
    }

    public static String fulfillmentId(String demandId, String supplyId, FulfillmentType type) {
        return "FF-" + demandId + "-" + supplyId + "-" + type.name();
    }

    public static String bomDependencyId(String parentSupplyOrderId, String childSupplyOrderId) {
        return "BOM-DEP-" + parentSupplyOrderId + "->" + childSupplyOrderId;
    }

    /** 与 {@link com.plantops.scenario.TimeslotHorizonService} 日槽 ID 一致。 */
    public static String schedulingSlotDayId(String resourceId, int dayOffset) {
        return resourceId + "-D" + dayOffset;
    }

    /** 与 {@link com.plantops.scenario.TimeslotHorizonService} 周槽 ID 一致。 */
    public static String schedulingSlotWeekId(String resourceId, int weekOffset) {
        return resourceId + "-W" + weekOffset;
    }

    public static String routingId(String pispId) {
        return "RT-" + pispId;
    }

    public static String routingStepId(String pispId, int sequenceNo) {
        return "RS-" + pispId + "-" + sequenceNo;
    }

    public static String routingStepOnStandardResourceId(String routingStepId, String standardResourceId) {
        return "RSOSR-" + routingStepId + "-" + standardResourceId;
    }

    public static String routingStepInputMaterialId(String routingStepId, String componentProductCode) {
        return "RSIN-" + routingStepId + "-" + componentProductCode;
    }

    public static String routingStepOutputMaterialId(String routingStepId, String outputProductCode) {
        return "RSOUT-" + routingStepId + "-" + outputProductCode;
    }
}
