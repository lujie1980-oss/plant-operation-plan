package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.MasterPlanKpiDtos.BusinessKpiDto;
import com.plantops.config.ParameterRegistry;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.ResourceCapacityAssignment;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.scenario.SrpLeafCapacitySupport;
import com.plantops.scenario.SrpLoadBucketProjector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Computes §15 KPI-MP-B01~B10 from ontology + plan version metadata. */
@ApplicationScoped
public class MasterPlanBusinessKpiCalculator {

    @Inject
    ParameterRegistry parameters;

    @Inject
    SrpLoadBucketProjector srpLoadBucketProjector;

    public List<BusinessKpiDto> compute(PlanVersionEntity version, OntologyGraph graph) {
        List<BusinessKpiDto> out = new ArrayList<>(10);
        int threshold = parameters.getInt("capacity_overload_threshold_pct", 110);
        long solveMs = version != null && version.solveDurationMs != null ? version.solveDurationMs : 0L;

        DeliveryMetrics delivery = deliveryMetrics(graph);
        CapacityMetrics capacity = capacityMetrics(graph, threshold);
        OperationMetrics operations = operationMetrics(graph);

        out.add(business("KPI-MP-B01", "计划 OTIF 率", delivery.otifPct(), "%", otifSeverity(delivery.otifPct())));
        out.add(business("KPI-MP-B02", "计划延期订单数", delivery.lateCount(), "单", delivery.lateCount() > 0 ? "danger" : "ok"));
        out.add(business("KPI-MP-B03", "承诺交期偏差 P95（天）", delivery.deviationP95Days(), "天", deviationSeverity(delivery.deviationP95Days())));
        out.add(business("KPI-MP-B04", "瓶颈资源利用率", capacity.maxUtilPct(), "%", capacity.maxUtilPct() >= 90 ? "warn" : "info"));
        out.add(business("KPI-MP-B05", "超载 period 占比", capacity.overloadSrpPct(), "%", capacity.overloadSrpPct() > 0 ? "danger" : "ok"));
        out.add(business("KPI-MP-B06", "制造周期 P95（天）", operations.cycleP95Days(), "天", "info"));
        out.add(business("KPI-MP-B07", "工序间等待占比", operations.waitRatioPct(), "%", operations.waitRatioPct() > 30 ? "warn" : "ok"));
        long materialShortage = materialShortagePeriods(graph);
        long unscheduled = unscheduledOperations(graph);
        out.add(business("KPI-MP-B08", "物料缺口 period 数", materialShortage, "个", materialShortage > 0 ? "warn" : "ok"));
        out.add(business("KPI-MP-B09", "未排程工序数", unscheduled, "道", unscheduled > 0 ? "danger" : "ok"));
        out.add(business("KPI-MP-B10", "主计划求解耗时", solveMs, "ms", solveMs > 60_000 ? "warn" : "ok"));
        return List.copyOf(out);
    }

    private DeliveryMetrics deliveryMetrics(OntologyGraph graph) {
        if (graph == null) {
            return DeliveryMetrics.empty();
        }
        int total = 0;
        int onTime = 0;
        int late = 0;
        List<Double> deviations = new ArrayList<>();
        for (CustomerOrderLineDelivery cold : graph.customerOrderLineDeliveriesById().values()) {
            if (cold == null || "CANCELLED".equalsIgnoreCase(cold.getStatus())) {
                continue;
            }
            LocalDate planned = plannedCompletionForDelivery(graph, cold.getId());
            if (planned == null) {
                continue;
            }
            LocalDate promise = promiseDate(graph, cold);
            if (promise == null) {
                continue;
            }
            total++;
            long deviationDays = ChronoUnit.DAYS.between(promise, planned);
            deviations.add((double) deviationDays);
            if (!planned.isAfter(promise)) {
                onTime++;
            }
            LocalDate latestDesired = cold.getLatestDesiredDate();
            if (latestDesired != null && planned.isAfter(latestDesired)) {
                late++;
            }
        }
        double otif = total == 0 ? 0.0 : onTime * 100.0 / total;
        double p95 = percentile(deviations, 95);
        return new DeliveryMetrics(otif, late, Math.abs(p95));
    }

    private static LocalDate promiseDate(OntologyGraph graph, CustomerOrderLineDelivery cold) {
        CustomerOrderLine line = graph.customerOrderLine(cold.getCustomerOrderLineId());
        if (line != null) {
            SalesOrderLineEntity entity = SalesOrderLineEntity.findByKey(line.getSalesOrderNo(), line.getSalesOrderLineNo());
            if (entity != null && entity.promiseDate != null) {
                return entity.promiseDate;
            }
        }
        if (cold.getLatestDesiredDate() != null) {
            return cold.getLatestDesiredDate();
        }
        return cold.getRequestedDate();
    }

    private static LocalDate plannedCompletionForDelivery(OntologyGraph graph, String coldId) {
        Demand demand = graph.demandsById().values().stream()
                .filter(d -> d.getSourceType() == DemandSourceType.CUSTOMER_DELIVERY)
                .filter(d -> coldId.equals(d.getSourceId()))
                .findFirst()
                .orElse(null);
        if (demand == null) {
            return null;
        }
        Set<String> supplyOrderIds = new HashSet<>();
        for (Fulfillment ff : graph.fulfillments()) {
            if (!demand.getId().equals(ff.getDemandId())) {
                continue;
            }
            Supply supply = graph.supply(ff.getSupplyId());
            if (supply != null && supply.getSupplyOrderId() != null) {
                supplyOrderIds.add(supply.getSupplyOrderId());
            }
        }
        LocalDateTime maxEnd = null;
        for (String supplyOrderId : supplyOrderIds) {
            for (Operation op : graph.operationsForSupplyOrder(supplyOrderId)) {
                LocalDateTime end = op.getPlannedEndTotal();
                if (end != null && (maxEnd == null || end.isAfter(maxEnd))) {
                    maxEnd = end;
                }
            }
        }
        return maxEnd != null ? maxEnd.toLocalDate() : null;
    }

    private CapacityMetrics capacityMetrics(OntologyGraph graph, int thresholdPct) {
        if (graph == null) {
            return CapacityMetrics.empty();
        }
        List<StandardResourcePeriod> leaf = SrpLeafCapacitySupport.leafSrps(graph);
        double maxUtil = 0.0;
        for (StandardResourcePeriod srp : leaf) {
            maxUtil = Math.max(maxUtil, SrpLeafCapacitySupport.utilizationPct(srp));
        }
        SrpLoadBucketProjector.SrpLoadBucketResult bucketResult =
                srpLoadBucketProjector.project(graph, null, thresholdPct);
        double overloadPct = bucketResult.leafSrpCount() == 0
                ? 0.0
                : bucketResult.overloadedLeafSrpCount() * 100.0 / bucketResult.leafSrpCount();
        return new CapacityMetrics(maxUtil, overloadPct);
    }

    private OperationMetrics operationMetrics(OntologyGraph graph) {
        if (graph == null) {
            return OperationMetrics.empty();
        }
        List<Double> cycleDays = new ArrayList<>();
        long totalWaitMinutes = 0;
        long totalMakespanMinutes = 0;
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            List<Operation> ops = graph.operationsForSupplyOrder(supplyOrder.getId()).stream()
                    .filter(op -> op.getPlannedStartTotal() != null && op.getPlannedEndTotal() != null)
                    .sorted(Comparator.comparingInt(Operation::getSequenceNr))
                    .toList();
            if (ops.isEmpty()) {
                continue;
            }
            for (Operation op : ops) {
                long minutes = ChronoUnit.MINUTES.between(op.getPlannedStartTotal(), op.getPlannedEndTotal());
                if (minutes > 0) {
                    cycleDays.add(minutes / (24.0 * 60.0));
                }
            }
            LocalDateTime chainStart = ops.getFirst().getPlannedStartTotal();
            LocalDateTime chainEnd = ops.getLast().getPlannedEndTotal();
            if (chainStart != null && chainEnd != null && chainEnd.isAfter(chainStart)) {
                totalMakespanMinutes += ChronoUnit.MINUTES.between(chainStart, chainEnd);
            }
            for (int i = 1; i < ops.size(); i++) {
                Operation prev = ops.get(i - 1);
                Operation next = ops.get(i);
                if (prev.getPlannedEndTotal() != null && next.getPlannedStartTotal() != null) {
                    long gap = ChronoUnit.MINUTES.between(prev.getPlannedEndTotal(), next.getPlannedStartTotal());
                    if (gap > 0) {
                        totalWaitMinutes += gap;
                    }
                }
            }
        }
        double waitRatio = totalMakespanMinutes <= 0 ? 0.0 : totalWaitMinutes * 100.0 / totalMakespanMinutes;
        return new OperationMetrics(percentile(cycleDays, 95), waitRatio);
    }

    private static long materialShortagePeriods(OntologyGraph graph) {
        if (graph == null) {
            return 0;
        }
        return graph.pispPeriodsById().values().stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getStockShortageQuantity() > 0)
                .count();
    }

    private static long unscheduledOperations(OntologyGraph graph) {
        if (graph == null) {
            return 0;
        }
        Set<String> scheduled = new HashSet<>();
        for (ResourceCapacityAssignment rca : graph.resourceCapacityAssignmentsById().values()) {
            if (rca.getOperationId() != null) {
                scheduled.add(rca.getOperationId());
            }
        }
        return graph.operationsById().values().stream()
                .filter(op -> !scheduled.contains(op.getId()))
                .count();
    }

    private static double percentile(List<Double> values, double pct) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private static BusinessKpiDto business(String id, String name, double value, String unit, String severity) {
        return new BusinessKpiDto(id, name, value, unit, severity);
    }

    private static String otifSeverity(double otifPct) {
        if (otifPct >= 95) {
            return "ok";
        }
        if (otifPct >= 80) {
            return "warn";
        }
        return "danger";
    }

    private static String deviationSeverity(double days) {
        if (days <= 1) {
            return "ok";
        }
        if (days <= 5) {
            return "warn";
        }
        return "danger";
    }

    private record DeliveryMetrics(double otifPct, int lateCount, double deviationP95Days) {
        static DeliveryMetrics empty() {
            return new DeliveryMetrics(0, 0, 0);
        }
    }

    private record CapacityMetrics(double maxUtilPct, double overloadSrpPct) {
        static CapacityMetrics empty() {
            return new CapacityMetrics(0, 0);
        }
    }

    private record OperationMetrics(double cycleP95Days, double waitRatioPct) {
        static OperationMetrics empty() {
            return new OperationMetrics(0, 0);
        }
    }
}
