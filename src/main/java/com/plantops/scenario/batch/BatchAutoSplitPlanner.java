package com.plantops.scenario.batch;

import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.ProductRoutingSteps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 自动拆批启发式：min/max 夹紧 + 交期 + 产能，不接入 MRP。 */
public final class BatchAutoSplitPlanner {

    private BatchAutoSplitPlanner() {
    }

    /**
     * 计算目标批量（整数），已夹在 [minQty, maxQty]。
     */
    public static int computeTargetBatchSize(
            WorkOrderEntity wo,
            BigDecimal remainingQty,
            int preferredQty,
            int minQty,
            int maxQty,
            int shiftCapacityMinutes) {
        int min = Math.max(1, minQty);
        int max = Math.max(min, maxQty);
        int baseline = Math.max(min, Math.min(max, Math.max(1, preferredQty)));

        if (wo != null && wo.needDate != null) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), wo.needDate);
            if (daysLeft <= 3) {
                baseline = Math.max(min, baseline / 2);
            } else if (daysLeft >= 21) {
                baseline = Math.min(max, (int) Math.round(baseline * 1.5));
            }
        }

        baseline = shrinkForCapacity(wo, baseline, min, max, shiftCapacityMinutes);

        if (remainingQty != null && remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            int remainInt = remainingQty.setScale(0, RoundingMode.CEILING).intValue();
            if (remainInt < baseline) {
                baseline = Math.max(min, Math.min(max, remainInt));
            }
        }
        return Math.max(min, Math.min(max, baseline));
    }

    static int shrinkForCapacity(
            WorkOrderEntity wo,
            int baseline,
            int min,
            int max,
            int shiftCapacityMinutes) {
        if (wo == null || wo.productCode == null || shiftCapacityMinutes <= 0) {
            return baseline;
        }
        List<ProductRoutingSteps.Operation> operations =
                ProductRoutingSteps.operationsForProduct(wo.productCode);
        if (operations.isEmpty()) {
            return baseline;
        }
        BigDecimal runQty = BigDecimal.valueOf(baseline);
        int totalMinutes = 0;
        for (ProductRoutingSteps.Operation operation : operations) {
            totalMinutes += ProductRoutingSteps.durationMinutesForOperation(operation, runQty);
        }
        int capLimit = (int) Math.round(shiftCapacityMinutes * 0.85);
        if (totalMinutes > capLimit && totalMinutes > 0) {
            baseline = Math.max(min, (int) Math.floor(baseline * (double) capLimit / totalMinutes));
        }
        return Math.max(min, Math.min(max, baseline));
    }

    /**
     * 将剩余量拆成批次列表；尾批若小于 minQty 则并入前一批。
     */
    public static List<BigDecimal> planQuantities(
            BigDecimal remaining,
            int targetBatchSize,
            int minQty,
            int maxQty) {
        if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        int min = Math.max(1, minQty);
        int max = Math.max(min, maxQty);
        int target = Math.max(min, Math.min(max, targetBatchSize));

        if (remaining.compareTo(BigDecimal.valueOf(max)) <= 0) {
            return List.of(remaining.setScale(4, RoundingMode.HALF_UP));
        }

        List<BigDecimal> parts = new ArrayList<>(BatchFixedQuantitySplitter.split(
                remaining,
                BigDecimal.valueOf(target),
                BatchRemainderMode.SEPARATE_TAIL));

        mergeUndersizedTail(parts, min);
        enforceMaxBatchSize(parts, max);
        return parts;
    }

    private static void mergeUndersizedTail(List<BigDecimal> parts, int minQty) {
        while (parts.size() >= 2) {
            BigDecimal tail = parts.get(parts.size() - 1);
            if (tail.compareTo(BigDecimal.valueOf(minQty)) >= 0) {
                break;
            }
            BigDecimal prev = parts.get(parts.size() - 2);
            parts.set(parts.size() - 2, prev.add(tail).setScale(4, RoundingMode.HALF_UP));
            parts.remove(parts.size() - 1);
        }
    }

    private static void enforceMaxBatchSize(List<BigDecimal> parts, int maxQty) {
        BigDecimal max = BigDecimal.valueOf(maxQty);
        int i = 0;
        while (i < parts.size()) {
            BigDecimal qty = parts.get(i);
            if (qty.compareTo(max) <= 0) {
                i++;
                continue;
            }
            parts.set(i, max);
            parts.add(i + 1, qty.subtract(max).setScale(4, RoundingMode.HALF_UP));
            i++;
        }
    }
}
