package com.plantops.scenario;

import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/** 从 {@link OrderFulfillmentChainDto} 推断承诺交期（M5 Phase 2）。 */
public final class FulfillmentChainPromiseDate {

    private static final Set<String> PROMISE_NODE_TYPES = Set.of(
            "SALES_ORDER",
            "WORK_ORDER",
            "SUPPLY_ORDER");

    private FulfillmentChainPromiseDate() {
    }

    public static LocalDate suggest(OrderFulfillmentChainDto chain) {
        if (chain == null || chain.nodes() == null) {
            return null;
        }
        return chain.nodes().stream()
                .filter(node -> PROMISE_NODE_TYPES.contains(node.nodeType()))
                .map(FulfillmentChainPromiseDate::nodeEndDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static LocalDate nodeEndDate(FulfillmentChainNodeDto node) {
        if (node.endTs() != null) {
            return node.endTs().toLocalDate();
        }
        if (node.attributes() == null) {
            return null;
        }
        Object plannedEnd = node.attributes().get("plannedEndTs");
        if (plannedEnd == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(plannedEnd.toString()).toLocalDate();
        } catch (Exception ex) {
            String text = plannedEnd.toString();
            if (text.length() >= 10) {
                return LocalDate.parse(text.substring(0, 10));
            }
            return null;
        }
    }
}
