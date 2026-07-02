package com.plantops.ontology.material;

import com.plantops.api.dto.materialplanning.MaterialReservationDtos.AutoReservationRequest;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.AutoReservationResultDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.CreateFulfillmentRequest;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.EligibleSupplyRowDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.FulfillmentDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.ReservationAlertDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.persistence.OntologyP0UpsertService;
import com.plantops.ontology.persistence.OntologyRevisionService;
import com.plantops.ontology.supply.Supply;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class OntologyMaterialReservationService {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyRevisionService revisionService;

    @Inject
    OntologyP0UpsertService upsertService;

    @Inject
    OntologyMaterialEligibleSupplyProjector eligibleSupplyProjector;

    @Transactional
    public FulfillmentDto createFulfillment(CreateFulfillmentRequest request, String masterPlanVersionId) {
        if (request == null || request.demandId() == null || request.supplyId() == null) {
            throw new BadRequestException("demandId 与 supplyId 必填");
        }
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph graph = authoritativeOntologyGraph.getOrLoad(workspaceId, masterPlanVersionId);
        Demand demand = requireDemand(graph, request.demandId());
        Supply supply = requireSupply(graph, request.supplyId());
        validateSamePisp(demand, supply);
        if (OntologyMaterialReservationSupport.isShortageSupply(supply.getId())) {
            throw new BadRequestException("不得手工预留至缺口 Supply");
        }

        OntologyMaterialReservationSupport.SupplyAvailability availability =
                OntologyMaterialReservationSupport.resolveSupplyAvailability(graph, supply);
        double demandUnpegged = OntologyMaterialReservationSupport.unpeggedQtyForDemand(graph, demand);
        double supplyUnpegged = OntologyMaterialReservationSupport.unpeggedQtyForSupply(
                graph, supply, availability.availableQty());
        double qty = request.quantity() != null && request.quantity() > 0
                ? request.quantity()
                : Math.min(demandUnpegged, supplyUnpegged);
        if (qty <= 0) {
            throw new BadRequestException("预留数量必须大于 0");
        }
        if (qty > demandUnpegged + 1e-6 || qty > supplyUnpegged + 1e-6) {
            throw new BadRequestException("预留数量超过未预留量");
        }
        validateSupplyNotLaterThanDemand(demand, availability.availableDate());

        FulfillmentType type = OntologyMaterialReservationSupport.fulfillmentTypeForSupply(supply.getId());
        Fulfillment persisted = persistMergedFulfillment(
                workspaceId, masterPlanVersionId, graph, demand.getId(), supply.getId(), type, qty);
        authoritativeOntologyGraph.invalidate(workspaceId, masterPlanVersionId);

        OntologyGraph refreshed = authoritativeOntologyGraph.getOrLoad(workspaceId, masterPlanVersionId);
        return toDto(refreshed, persisted);
    }

    @Transactional
    public AutoReservationResultDto autoReserve(AutoReservationRequest request, String masterPlanVersionId) {
        if (request == null || request.anchorType() == null || request.anchorId() == null) {
            throw new BadRequestException("anchorType 与 anchorId 必填");
        }
        String anchorType = request.anchorType().trim().toUpperCase(Locale.ROOT);
        return switch (anchorType) {
            case "DEMAND" -> autoReserveFromDemand(request, masterPlanVersionId);
            case "SUPPLY" -> autoReserveFromSupply(request, masterPlanVersionId);
            default -> throw new BadRequestException("anchorType 须为 DEMAND 或 SUPPLY");
        };
    }

    public List<ReservationAlertDto> reservationAlerts(
            OntologyGraph graph,
            String pispId,
            String periodFrom,
            String periodTo) {
        OntologyMaterialSupplyRoutingService.requirePisp(graph, pispId);
        List<ReservationAlertDto> alerts = new ArrayList<>();

        for (Demand demand : OntologyMaterialReservationSupport.demandsForPisp(graph, pispId)) {
            if (!OntologyMaterialReservationSupport.dateInPeriodRange(
                    graph, demand.getNeedDate(), periodFrom, periodTo)) {
                continue;
            }
            double unpegged = OntologyMaterialReservationSupport.unpeggedQtyForDemand(graph, demand);
            if (unpegged > 0) {
                alerts.add(new ReservationAlertDto(
                        "UNALLOCATED_DEMAND",
                        demand.getId(),
                        null,
                        OntologyMaterialReservationSupport.periodIdForDate(graph, demand.getNeedDate()),
                        "Demand 未预留 " + unpegged));
            }
        }

        for (Supply supply : OntologyMaterialReservationSupport.suppliesForPisp(graph, pispId)) {
            if (OntologyMaterialReservationSupport.isShortageSupply(supply.getId())) {
                continue;
            }
            OntologyMaterialReservationSupport.SupplyAvailability availability =
                    OntologyMaterialReservationSupport.resolveSupplyAvailability(graph, supply);
            double unpegged = OntologyMaterialReservationSupport.unpeggedQtyForSupply(
                    graph, supply, availability.availableQty());
            if (unpegged <= 0) {
                continue;
            }
            String periodId = OntologyMaterialReservationSupport.periodIdForDate(
                    graph, availability.availableDate());
            if (!OntologyMaterialReservationSupport.dateInPeriodRange(
                    graph, availability.availableDate(), periodFrom, periodTo)) {
                continue;
            }
            alerts.add(new ReservationAlertDto(
                    "UNALLOCATED_SUPPLY",
                    null,
                    supply.getId(),
                    periodId,
                    "Supply 未预留 " + unpegged));
        }

        for (Fulfillment fulfillment : graph.fulfillments()) {
            Demand demand = graph.demand(fulfillment.getDemandId());
            Supply supply = graph.supply(fulfillment.getSupplyId());
            if (demand == null || supply == null || !pispId.equals(demand.getPispId())) {
                continue;
            }
            if (!OntologyMaterialReservationSupport.dateInPeriodRange(
                    graph, demand.getNeedDate(), periodFrom, periodTo)) {
                continue;
            }
            OntologyMaterialReservationSupport.SupplyAvailability availability =
                    OntologyMaterialReservationSupport.resolveSupplyAvailability(graph, supply);
            if (demand.getNeedDate() != null
                    && availability.availableDate() != null
                    && availability.availableDate().isAfter(demand.getNeedDate())) {
                alerts.add(new ReservationAlertDto(
                        "TIME_MISMATCH",
                        demand.getId(),
                        supply.getId(),
                        OntologyMaterialReservationSupport.periodIdForDate(graph, demand.getNeedDate()),
                        "Supply 可用日晚于 Demand needDate"));
            }
        }

        alerts.sort(Comparator
                .comparing(ReservationAlertDto::alertType)
                .thenComparing(a -> a.demandId() != null ? a.demandId() : a.supplyId()));
        return alerts;
    }

    private AutoReservationResultDto autoReserveFromDemand(
            AutoReservationRequest request, String masterPlanVersionId) {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph graph = authoritativeOntologyGraph.getOrLoad(workspaceId, masterPlanVersionId);
        Demand demand = requireDemand(graph, request.anchorId());
        double demandUnpegged = OntologyMaterialReservationSupport.unpeggedQtyForDemand(graph, demand);
        if (demandUnpegged <= 0) {
            return new AutoReservationResultDto(List.of(), 0, 0);
        }

        List<EligibleSupplyRowDto> candidates = eligibleSupplyProjector.project(graph, demand.getId()).supplies();
        EligibleSupplyRowDto chosen = candidates.stream()
                .filter(row -> row.availableDate() == null
                        || demand.getNeedDate() == null
                        || !row.availableDate().isAfter(demand.getNeedDate()))
                .sorted(Comparator
                        .comparing((EligibleSupplyRowDto row) -> !"INVENTORY".equals(row.supplyType()))
                        .thenComparing(EligibleSupplyRowDto::availableDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EligibleSupplyRowDto::unpeggedQty, Comparator.reverseOrder()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("无 eligible Supply 可自动预留"));

        double maxQty = request.maxQty() != null && request.maxQty() > 0
                ? request.maxQty()
                : Math.min(demandUnpegged, chosen.unpeggedQty());
        FulfillmentDto created = createFulfillment(
                new CreateFulfillmentRequest(demand.getId(), chosen.supplyId(), maxQty, "AUTO"),
                masterPlanVersionId);
        return new AutoReservationResultDto(
                List.of(created),
                created.quantity(),
                Math.max(0, demandUnpegged - created.quantity()));
    }

    private AutoReservationResultDto autoReserveFromSupply(
            AutoReservationRequest request, String masterPlanVersionId) {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph graph = authoritativeOntologyGraph.getOrLoad(workspaceId, masterPlanVersionId);
        Supply supply = requireSupply(graph, request.anchorId());
        OntologyMaterialReservationSupport.SupplyAvailability availability =
                OntologyMaterialReservationSupport.resolveSupplyAvailability(graph, supply);
        double supplyUnpegged = OntologyMaterialReservationSupport.unpeggedQtyForSupply(
                graph, supply, availability.availableQty());
        if (supplyUnpegged <= 0) {
            return new AutoReservationResultDto(List.of(), 0, 0);
        }

        Demand chosen = OntologyMaterialReservationSupport.demandsForPisp(graph, supply.getPispId()).stream()
                .filter(d -> supply.getProductCode().equals(d.getProductCode()))
                .filter(d -> OntologyMaterialReservationSupport.unpeggedQtyForDemand(graph, d) > 0)
                .filter(d -> d.getNeedDate() == null
                        || availability.availableDate() == null
                        || !availability.availableDate().isAfter(d.getNeedDate()))
                .sorted(Comparator
                        .comparing(Demand::getNeedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Comparator.comparingInt(Demand::getPriority).reversed())
                        .thenComparing(Demand::getId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("无 eligible Demand 可自动预留"));

        double demandUnpegged = OntologyMaterialReservationSupport.unpeggedQtyForDemand(graph, chosen);
        double maxQty = request.maxQty() != null && request.maxQty() > 0
                ? request.maxQty()
                : Math.min(demandUnpegged, supplyUnpegged);
        FulfillmentDto created = createFulfillment(
                new CreateFulfillmentRequest(chosen.getId(), supply.getId(), maxQty, "AUTO"),
                masterPlanVersionId);
        return new AutoReservationResultDto(
                List.of(created),
                created.quantity(),
                Math.max(0, supplyUnpegged - created.quantity()));
    }

    private Fulfillment persistMergedFulfillment(
            String workspaceId,
            String masterPlanVersionId,
            OntologyGraph graph,
            String demandId,
            String supplyId,
            FulfillmentType type,
            double quantity) {
        String revisionId = revisionService.resolveWorkspaceHeadRevisionId(workspaceId);
        if (revisionId == null) {
            throw new BadRequestException("Workspace 无 ont revision head，无法写入预留");
        }
        String fulfillmentId = OntologyIds.fulfillmentId(demandId, supplyId, type);
        double existingQty = graph.fulfillments().stream()
                .filter(ff -> fulfillmentId.equals(ff.getId()))
                .mapToDouble(Fulfillment::getQuantity)
                .findFirst()
                .orElse(0.0);
        Fulfillment fulfillment = new Fulfillment(
                fulfillmentId, demandId, supplyId, existingQty + quantity, type);
        upsertService.upsertFulfillment(fulfillment, workspaceId, revisionId);
        return fulfillment;
    }

    private static Demand requireDemand(OntologyGraph graph, String demandId) {
        Demand demand = graph.demand(demandId);
        if (demand == null) {
            throw new NotFoundException("Demand not found: " + demandId);
        }
        return demand;
    }

    private static Supply requireSupply(OntologyGraph graph, String supplyId) {
        Supply supply = graph.supply(supplyId);
        if (supply == null) {
            throw new NotFoundException("Supply not found: " + supplyId);
        }
        return supply;
    }

    private static void validateSamePisp(Demand demand, Supply supply) {
        if (demand.getPispId() == null || !demand.getPispId().equals(supply.getPispId())) {
            throw new BadRequestException("Demand 与 Supply 须同一 PISP");
        }
        if (!demand.getProductCode().equals(supply.getProductCode())) {
            throw new BadRequestException("Demand 与 Supply 物料不匹配");
        }
    }

    private static void validateSupplyNotLaterThanDemand(Demand demand, LocalDate availableDate) {
        if (demand.getNeedDate() != null
                && availableDate != null
                && availableDate.isAfter(demand.getNeedDate())) {
            throw new BadRequestException("Supply 可用日不得晚于 Demand needDate（RULE-FF-08）");
        }
    }

    private static FulfillmentDto toDto(OntologyGraph graph, Fulfillment fulfillment) {
        Demand demand = graph.demand(fulfillment.getDemandId());
        Supply supply = graph.supply(fulfillment.getSupplyId());
        OntologyMaterialReservationSupport.SupplyAvailability availability =
                supply != null
                        ? OntologyMaterialReservationSupport.resolveSupplyAvailability(graph, supply)
                        : new OntologyMaterialReservationSupport.SupplyAvailability(LocalDate.now(), 0, "UNKNOWN");
        return new FulfillmentDto(
                fulfillment.getId(),
                fulfillment.getDemandId(),
                fulfillment.getSupplyId(),
                fulfillment.getQuantity(),
                fulfillment.getType().name(),
                demand != null ? OntologyMaterialReservationSupport.unpeggedQtyForDemand(graph, demand) : 0,
                supply != null
                        ? OntologyMaterialReservationSupport.unpeggedQtyForSupply(
                                graph, supply, availability.availableQty())
                        : 0);
    }
}
