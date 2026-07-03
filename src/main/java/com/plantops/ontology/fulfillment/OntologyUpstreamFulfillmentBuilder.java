package com.plantops.ontology.fulfillment;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.supply.BomDependency;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.SupplyOrderStatus;
import com.plantops.ontology.supply.SupplyOrderType;
import com.plantops.persistence.entity.InventoryEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在本体图内为指定 {@link com.plantops.ontology.demand.CustomerOrderLineDelivery} 递归构建上游满足链：
 * 库存 / 已有 Supply → Fulfillment；缺口 → 合成 SupplyOrder + JIT 工序时间窗 → 子件 Demand 继续向上。
 */
@ApplicationScoped
public class OntologyUpstreamFulfillmentBuilder {

    @Inject
    OntologySupplyOrderMaterializer supplyOrderMaterializer;

    @Inject
    BomDependencyDerivation bomDependencyDerivation;

    public void buildForDelivery(
            OntologyGraph.Builder builder,
            String deliveryId,
            LocalDate planningStart,
            UpstreamFulfillmentSession session) {
        String demandId = OntologyIds.demandFromCustomerDeliveryId(deliveryId);
        Demand rootDemand = builder.demandsById().get(demandId);
        if (rootDemand == null) {
            return;
        }
        builder.fulfillments().clear();
        builder.bomDependencies().clear();

        PeggingState state = PeggingState.fromWorkspaceInventory();
        ensureBaseSupplies(builder, state);

        String finishedProduct = rootDemand.getProductCode();
        satisfyDemand(builder, rootDemand, finishedProduct, null, state, planningStart, session);

        bomDependencyDerivation.derive(builder);
    }

    private void satisfyDemand(
            OntologyGraph.Builder builder,
            Demand demand,
            String finishedProduct,
            String parentSupplyOrderId,
            PeggingState state,
            LocalDate planningStart,
            UpstreamFulfillmentSession session) {
        if (!state.seenDemands.add(demand.getId())) {
            return;
        }

        double remaining = demand.getQuantity();
        if (remaining <= 0) {
            return;
        }

        remaining = pegInventory(builder, demand, remaining, state);
        if (remaining <= 0) {
            return;
        }

        remaining = pegExistingSupplies(builder, demand, remaining, state);
        if (remaining <= 0) {
            return;
        }

        if (!session.hasManufacturingRouting(demand.getProductCode())) {
            pegShortage(builder, demand, remaining);
            return;
        }

        SupplyOrder supplyOrder = createSyntheticSupplyOrder(builder, demand, remaining, planningStart, session);
        List<Demand> bomDemands = ensureMaterialized(
                builder, supplyOrder, finishedProduct, planningStart, session);

        String outputSupplyId = OntologyIds.supplyId(supplyOrder.getId(), 0);
        addFulfillment(builder, demand.getId(), outputSupplyId, remaining, FulfillmentType.WORK_ORDER_PEG);
        state.consumeSupply(outputSupplyId, remaining);

        if (parentSupplyOrderId != null) {
            builder.bomDependency(new BomDependency(
                    OntologyIds.bomDependencyId(parentSupplyOrderId, supplyOrder.getId()),
                    parentSupplyOrderId,
                    supplyOrder.getId()));
        }

        for (Demand bomDemand : bomDemands) {
            satisfyDemand(
                    builder,
                    bomDemand,
                    supplyOrder.getProductCode(),
                    supplyOrder.getId(),
                    state,
                    planningStart,
                    session);
        }
    }

    private static double pegInventory(
            OntologyGraph.Builder builder,
            Demand demand,
            double remaining,
            PeggingState state) {
        String productCode = demand.getProductCode();
        double available = state.inventoryAvailable.getOrDefault(productCode, 0.0);
        if (available <= 0) {
            return remaining;
        }
        double pegQty = Math.min(available, remaining);
        String supplyId = OntologyIds.inventorySupplyId(productCode);
        addFulfillment(builder, demand.getId(), supplyId, pegQty, FulfillmentType.INVENTORY_PEG);
        state.inventoryAvailable.put(productCode, available - pegQty);
        return remaining - pegQty;
    }

    private static double pegExistingSupplies(
            OntologyGraph.Builder builder,
            Demand demand,
            double remaining,
            PeggingState state) {
        List<Supply> candidates = builder.suppliesById().values().stream()
                .filter(s -> demand.getProductCode().equals(s.getProductCode()))
                .filter(s -> s.getSupplyOrderId() != null)
                .sorted(Comparator.comparing((Supply s) -> {
                    SupplyOrder so = builder.supplyOrdersById().get(s.getSupplyOrderId());
                    return so != null && so.getNeedDate() != null ? so.getNeedDate() : LocalDate.MAX;
                }))
                .toList();

        double left = remaining;
        for (Supply supply : candidates) {
            if (left <= 0) {
                break;
            }
            double available = state.availableSupplyQty(supply.getId(), supply.getQuantity());
            if (available <= 0) {
                continue;
            }
            double pegQty = Math.min(available, left);
            addFulfillment(builder, demand.getId(), supply.getId(), pegQty, FulfillmentType.WORK_ORDER_PEG);
            state.consumeSupply(supply.getId(), pegQty);
            left -= pegQty;
        }
        return left;
    }

    private static void pegShortage(OntologyGraph.Builder builder, Demand demand, double qty) {
        String productCode = demand.getProductCode();
        String supplyId = OntologyIds.shortageSupplyId(productCode);
        if (!builder.suppliesById().containsKey(supplyId)) {
            builder.supply(new Supply(
                    supplyId,
                    productCode,
                    OntologyIds.pispId(productCode),
                    0.0,
                    null));
        }
        addFulfillment(builder, demand.getId(), supplyId, qty, FulfillmentType.SHORTAGE_PEG);
    }

    private List<Demand> ensureMaterialized(
            OntologyGraph.Builder builder,
            SupplyOrder supplyOrder,
            String finishedProduct,
            LocalDate planningStart,
            UpstreamFulfillmentSession session) {
        String outputSupplyId = OntologyIds.supplyId(supplyOrder.getId(), 0);
        if (builder.suppliesById().containsKey(outputSupplyId)) {
            return builder.demandsById().values().stream()
                    .filter(d -> d.getSourceType() == DemandSourceType.BOM_COMPONENT)
                    .filter(d -> supplyOrder.getId().equals(d.getSourceId()))
                    .toList();
        }
        return supplyOrderMaterializer.materialize(builder, supplyOrder, finishedProduct, planningStart, session);
    }

    private SupplyOrder createSyntheticSupplyOrder(
            OntologyGraph.Builder builder,
            Demand demand,
            double quantity,
            LocalDate planningStart,
            UpstreamFulfillmentSession session) {
        LocalDate needDate = demand.getNeedDate() != null ? demand.getNeedDate() : planningStart;
        String supplyOrderId = session.allocateWorkOrderNo(demand.getProductCode(), needDate);
        SupplyOrder existing = builder.supplyOrdersById().get(supplyOrderId);
        if (existing != null) {
            return existing;
        }
        SupplyOrder supplyOrder = new SupplyOrder(
                supplyOrderId,
                demand.getProductCode(),
                demand.getPispId(),
                quantity,
                needDate,
                SupplyOrderStatus.OPEN,
                SupplyOrderType.PLANNED_PRODUCTION);
        builder.supplyOrder(supplyOrder);
        return supplyOrder;
    }

    private static void ensureBaseSupplies(OntologyGraph.Builder builder, PeggingState state) {
        for (String productCode : state.inventoryAvailable.keySet()) {
            String invId = OntologyIds.inventorySupplyId(productCode);
            if (!builder.suppliesById().containsKey(invId)) {
                builder.supply(new Supply(
                        invId,
                        productCode,
                        OntologyIds.pispId(productCode),
                        state.inventoryAvailable.get(productCode),
                        null));
            }
            String shortId = OntologyIds.shortageSupplyId(productCode);
            if (!builder.suppliesById().containsKey(shortId)) {
                builder.supply(new Supply(
                        shortId,
                        productCode,
                        OntologyIds.pispId(productCode),
                        0.0,
                        null));
            }
        }
        for (Demand demand : builder.demandsById().values()) {
            String productCode = demand.getProductCode();
            String shortId = OntologyIds.shortageSupplyId(productCode);
            if (!builder.suppliesById().containsKey(shortId)) {
                builder.supply(new Supply(
                        shortId,
                        productCode,
                        OntologyIds.pispId(productCode),
                        0.0,
                        null));
            }
        }
    }

    private static void addFulfillment(
            OntologyGraph.Builder builder,
            String demandId,
            String supplyId,
            double quantity,
            FulfillmentType type) {
        builder.fulfillment(new Fulfillment(
                OntologyIds.fulfillmentId(demandId, supplyId, type),
                demandId,
                supplyId,
                quantity,
                type));
    }

    private static final class PeggingState {
        final Map<String, Double> inventoryAvailable = new LinkedHashMap<>();
        final Map<String, Double> supplyConsumed = new HashMap<>();
        final Set<String> seenDemands = new HashSet<>();

        static PeggingState fromWorkspaceInventory() {
            PeggingState state = new PeggingState();
            for (InventoryEntity row : InventoryEntity.listInWorkspace()) {
                if (row.productCode == null || row.productCode.isBlank()) {
                    continue;
                }
                double qty = row.availableQty().doubleValue();
                if (qty <= 0) {
                    continue;
                }
                state.inventoryAvailable.merge(row.productCode, qty, Double::sum);
            }
            return state;
        }

        double availableSupplyQty(String supplyId, double supplyQty) {
            double consumed = supplyConsumed.getOrDefault(supplyId, 0.0);
            return Math.max(0.0, supplyQty - consumed);
        }

        void consumeSupply(String supplyId, double qty) {
            supplyConsumed.merge(supplyId, qty, Double::sum);
        }
    }
}
