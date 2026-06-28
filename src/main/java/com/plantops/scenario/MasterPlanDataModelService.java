package com.plantops.scenario;

import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.MasterPlanDataModelTreeDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.MasterPlanPispRoutingDetailDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.ProductInStockingPointNodeDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepDetailDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.StockingPointNodeDto;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.BomStockingPointTierResolver;
import com.plantops.ontology.master.BomStockingPointTierResolver.TierAssignment;
import com.plantops.ontology.master.MasterPlanRoutingProjector;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.master.StockingPoint;
import com.plantops.persistence.entity.MaterialEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MasterPlanDataModelService {

    @Inject
    MasterPlanRoutingProjector routingProjector;

    @Inject
    BomStockingPointTierResolver stockingPointTierResolver;

    public MasterPlanDataModelTreeDto listTree() {
        Map<String, TierAssignment> assignments = stockingPointTierResolver.assignAllMaterials();
        Map<String, List<ProductInStockingPointNodeDto>> pispsBySp = new LinkedHashMap<>();
        for (StockingPoint sp : stockingPointTierResolver.orderedStockingPoints()) {
            pispsBySp.put(sp.getId(), new ArrayList<>());
        }

        assignments.values().stream()
                .sorted(Comparator.comparing(TierAssignment::productCode))
                .forEach(assignment -> {
                    ProductInStockingPointNodeDto node = toPispNode(assignment);
                    pispsBySp.computeIfAbsent(assignment.stockingPointId(), k -> new ArrayList<>()).add(node);
                });

        List<StockingPointNodeDto> stockingPoints = stockingPointTierResolver.orderedStockingPoints().stream()
                .map(sp -> new StockingPointNodeDto(
                        sp.getId(),
                        sp.getStockingPointCode(),
                        sp.getDisplayName(),
                        List.copyOf(pispsBySp.getOrDefault(sp.getId(), List.of()))))
                .toList();
        return new MasterPlanDataModelTreeDto(stockingPoints);
    }

    public MasterPlanPispRoutingDetailDto routingDetail(String pispId) {
        ProductInStockingPoint pisp = resolvePisp(pispId);
        TierAssignment assignment = stockingPointTierResolver.assignAllMaterials()
                .getOrDefault(pisp.getProductCode(), defaultAssignment(pisp));
        ProductInStockingPointNodeDto pispNode = toPispNode(assignment);
        if (!pispNode.hasRouting()) {
            return new MasterPlanPispRoutingDetailDto(pispNode, null, List.of());
        }
        RoutingDto routing = routingProjector.projectRoutingHeader(pispId, pisp.getProductCode());
        List<RoutingStepDetailDto> steps = routingProjector.projectRoutingSteps(pispId, pisp.getProductCode());
        return new MasterPlanPispRoutingDetailDto(pispNode, routing, steps);
    }

    private static TierAssignment defaultAssignment(ProductInStockingPoint pisp) {
        return new TierAssignment(
                pisp.getProductCode(),
                3,
                StockingPoint.RAW,
                BomStockingPointTierResolver.tierLabel(3));
    }

    private static ProductInStockingPoint resolvePisp(String pispId) {
        if (pispId == null || pispId.isBlank() || !pispId.startsWith("PISP-")) {
            throw new NotFoundException("PISP not found: " + pispId);
        }
        for (String stockingPointId : StockingPoint.knownIds()) {
            String suffix = "-" + stockingPointId;
            if (!pispId.endsWith(suffix)) {
                continue;
            }
            String productCode = pispId.substring("PISP-".length(), pispId.length() - suffix.length());
            if (productCode.isBlank()) {
                continue;
            }
            if (MaterialEntity.findByCode(productCode) == null) {
                throw new NotFoundException("PISP not found: " + pispId);
            }
            return new ProductInStockingPoint(pispId, productCode, stockingPointId, productCode);
        }
        throw new NotFoundException("PISP not found: " + pispId);
    }

    private ProductInStockingPointNodeDto toPispNode(TierAssignment assignment) {
        MaterialEntity material = MaterialEntity.findByCode(assignment.productCode());
        String productName = material != null && material.materialName != null && !material.materialName.isBlank()
                ? material.materialName
                : assignment.productCode();
        String pispId = OntologyIds.pispId(assignment.productCode(), assignment.stockingPointId());
        boolean hasRouting = !StockingPoint.RAW.equals(assignment.stockingPointId())
                && MasterPlanRoutingProjector.hasRouting(assignment.productCode());
        return new ProductInStockingPointNodeDto(
                pispId,
                assignment.productCode(),
                productName,
                assignment.stockingPointId(),
                assignment.tierFromTop(),
                assignment.tierLabel(),
                hasRouting);
    }
}
