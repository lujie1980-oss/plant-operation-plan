package com.plantops.ontology.supply;

import com.plantops.api.dto.WorkOrderTimingWindowDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 将 {@link WorkOrderTimingWindowDto} 分配到工序级时间窗。 */
public final class OperationTimingBridge {

    private OperationTimingBridge() {
    }

    public static void applyWorkOrderWindow(
            List<Operation> operations,
            WorkOrderTimingWindowDto window,
            LocalDate planningStart,
            LocalDate needDate) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        if (window == null) {
            OperationTimeWindowDerivations.recalculateFallback(operations, planningStart, needDate);
            return;
        }
        applyOwnBounds(operations, window.earliestPossibleStartOwn());
        applyTotalBounds(operations, window.earliestPossibleStart());
        applyLatestDesired(operations, window.latestDesiredEnd());
        applyInfeasible(operations);
    }

    /** needDate 变更时仅重算 JIT 最晚要求，保留 Own/Total。 */
    public static void recalculateLatestDesired(
            List<Operation> operations,
            LocalDate planningStart,
            LocalDate needDate,
            WorkOrderTimingWindowDto window) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        LocalDateTime latestEnd;
        if (needDate != null) {
            latestEnd = OperationTimeAnchor.needDateEnd(needDate, planningStart);
            Operation last = operations.get(operations.size() - 1);
            if (last.getPostprocessingTime() > 0) {
                latestEnd = OperationTimeAnchor.minusElapsed(latestEnd, last.getPostprocessingTime());
            }
        } else if (window != null && window.latestDesiredEnd() != null) {
            latestEnd = window.latestDesiredEnd();
        } else {
            return;
        }
        applyLatestDesired(operations, latestEnd);
        applyInfeasible(operations);
    }

    private static void applyOwnBounds(List<Operation> operations, LocalDateTime startOwn) {
        LocalDateTime effectiveStart = startOwn != null ? startOwn : LocalDateTime.now();
        for (Operation operation : operations) {
            operation.clearPlannedTimes();
            operation.setEarliestPossibleStartOwn(effectiveStart);
            operation.setEarliestPossibleEndOwn(
                    OperationTimeAnchor.plusElapsed(effectiveStart, operation.totalElapsedSeconds()));
        }
    }

    private static void applyTotalBounds(List<Operation> operations, LocalDateTime firstOpStartTotal) {
        LocalDateTime chainStart = firstOpStartTotal;
        if (chainStart == null && !operations.isEmpty()) {
            chainStart = operations.get(0).getEarliestPossibleStartOwn();
        }
        for (Operation operation : operations) {
            if (chainStart == null) {
                break;
            }
            operation.setEarliestPossibleStartTotal(chainStart);
            LocalDateTime chainEnd = OperationTimeAnchor.plusElapsed(chainStart, operation.totalElapsedSeconds());
            operation.setEarliestPossibleEndTotal(chainEnd);
            chainStart = chainEnd;
        }
    }

    private static void applyLatestDesired(List<Operation> operations, LocalDateTime lastOpLatestEnd) {
        if (lastOpLatestEnd == null) {
            return;
        }
        LocalDateTime latestEnd = lastOpLatestEnd;
        for (int i = operations.size() - 1; i >= 0; i--) {
            Operation operation = operations.get(i);
            long elapsed = operation.totalElapsedSeconds();
            operation.setLatestDesiredEnd(latestEnd);
            operation.setLatestDesiredStart(OperationTimeAnchor.minusElapsed(latestEnd, elapsed));
            latestEnd = operation.getLatestDesiredStart();
        }
    }

    private static void applyInfeasible(List<Operation> operations) {
        for (Operation operation : operations) {
            LocalDateTime earliest = operation.getEarliestPossibleStartTotal();
            LocalDateTime latest = operation.getLatestDesiredEnd();
            operation.setInfeasible(earliest != null && latest != null && earliest.isAfter(latest));
        }
    }
}
