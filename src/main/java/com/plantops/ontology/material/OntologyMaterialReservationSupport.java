package com.plantops.ontology.material;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.InventoryEntity;
import jakarta.ws.rs.BadRequestException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class OntologyMaterialReservationSupport {

    private OntologyMaterialReservationSupport() {
    }

    public static double peggedQtyForDemand(OntologyGraph graph, String demandId) {
        return graph.fulfillments().stream()
                .filter(ff -> demandId.equals(ff.getDemandId()))
                .mapToDouble(Fulfillment::getQuantity)
                .sum();
    }

    public static double peggedQtyForSupply(OntologyGraph graph, String supplyId) {
        return graph.fulfillments().stream()
                .filter(ff -> supplyId.equals(ff.getSupplyId()))
                .mapToDouble(Fulfillment::getQuantity)
                .sum();
    }

    public static double unpeggedQtyForDemand(OntologyGraph graph, Demand demand) {
        return Math.max(0, demand.getQuantity() - peggedQtyForDemand(graph, demand.getId()));
    }

    public static double unpeggedQtyForSupply(OntologyGraph graph, Supply supply, double availableQty) {
        return Math.max(0, availableQty - peggedQtyForSupply(graph, supply.getId()));
    }

    public static boolean isShortageSupply(String supplyId) {
        return supplyId != null && supplyId.startsWith("SUP-SHORT-");
    }

    public static boolean isInventorySupply(String supplyId) {
        return supplyId != null && supplyId.startsWith("SUP-INV-");
    }

    public static FulfillmentType fulfillmentTypeForSupply(String supplyId) {
        if (isInventorySupply(supplyId)) {
            return FulfillmentType.INVENTORY_PEG;
        }
        if (isShortageSupply(supplyId)) {
            throw new BadRequestException("不得手工预留至缺口 Supply（PEG-SH）");
        }
        return FulfillmentType.WORK_ORDER_PEG;
    }

    public static String supplyTypeLabel(String supplyId) {
        if (isInventorySupply(supplyId)) {
            return "INVENTORY";
        }
        if (isShortageSupply(supplyId)) {
            return "SHORTAGE";
        }
        return "WORK_ORDER";
    }

    public static SupplyAvailability resolveSupplyAvailability(OntologyGraph graph, Supply supply) {
        if (supply == null) {
            return new SupplyAvailability(LocalDate.now(), 0, supplyTypeLabel(null));
        }
        if (isInventorySupply(supply.getId())) {
            double qty = InventoryEntity.listInWorkspace().stream()
                    .filter(row -> supply.getProductCode().equals(row.productCode))
                    .mapToDouble(row -> row.availableQty().doubleValue())
                    .sum();
            return new SupplyAvailability(LocalDate.now(), qty, "INVENTORY");
        }
        if (isShortageSupply(supply.getId())) {
            return new SupplyAvailability(LocalDate.now(), 0, "SHORTAGE");
        }
        LocalDate availableDate = LocalDate.now();
        double qty = supply.getQuantity();
        if (supply.getSupplyOrderId() != null) {
            SupplyOrder supplyOrder = graph.supplyOrder(supply.getSupplyOrderId());
            if (supplyOrder != null && supplyOrder.getNeedDate() != null) {
                availableDate = supplyOrder.getNeedDate();
            }
            if (qty <= 0 && supplyOrder != null) {
                qty = supplyOrder.getQuantity();
            }
        }
        return new SupplyAvailability(availableDate, qty, "WORK_ORDER");
    }

    public static String periodIdForDate(OntologyGraph graph, LocalDate date) {
        PeriodIndex index = PeriodIndex.of(graph.periodsOrdered());
        Period period = index.periodAt(index.sequenceFor(date));
        return period != null ? period.getId() : null;
    }

    public static Period resolvePeriod(OntologyGraph graph, String periodId, String paramName) {
        if (periodId == null || periodId.isBlank()) {
            throw new BadRequestException(paramName + " 必填");
        }
        return graph.periodsOrdered().stream()
                .filter(p -> periodId.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("未知 periodId: " + periodId));
    }

    public static int sequenceForPeriod(OntologyGraph graph, String periodId) {
        return resolvePeriod(graph, periodId, "periodId").getSequenceNr();
    }

    public static boolean dateInPeriodRange(
            OntologyGraph graph, LocalDate date, String periodFrom, String periodTo) {
        if (date == null) {
            return false;
        }
        int fromSeq = sequenceForPeriod(graph, periodFrom);
        int toSeq = sequenceForPeriod(graph, periodTo);
        if (fromSeq > toSeq) {
            throw new BadRequestException("periodFrom 不得晚于 periodTo");
        }
        PeriodIndex index = PeriodIndex.of(graph.periodsOrdered());
        int seq = index.sequenceFor(date);
        return seq >= fromSeq && seq <= toSeq;
    }

    public static List<Demand> demandsForPisp(OntologyGraph graph, String pispId) {
        return graph.demandsById().values().stream()
                .filter(d -> pispId.equals(d.getPispId()))
                .toList();
    }

    public static List<Supply> suppliesForPisp(OntologyGraph graph, String pispId) {
        return graph.suppliesById().values().stream()
                .filter(s -> pispId.equals(s.getPispId()))
                .toList();
    }

    public static String inventorySupplyId(String productCode) {
        return OntologyIds.inventorySupplyId(productCode);
    }

    public record SupplyAvailability(LocalDate availableDate, double availableQty, String supplyType) {
    }
}
