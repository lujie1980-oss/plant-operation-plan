package com.plantops.ontology.material;

import com.plantops.api.dto.MaterialDemandDetailDto;
import com.plantops.api.dto.MaterialDemandTreeNodeDto;
import com.plantops.api.dto.MaterialDemandUsageDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从 {@link OntologyGraph} 的需求节点投影物料所支撑的上游需求树与用量列表。
 */
@ApplicationScoped
public class OntologyMaterialDemandProjector {

    public MaterialDemandDetailDto buildDemandDetail(OntologyGraph graph, String materialCode) {
        List<MaterialDemandTreeNodeDto> roots = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        int pathCount = 0;

        for (Demand demand : graph.demandsById().values()) {
            if (!materialCode.equals(demand.getProductCode())) {
                continue;
            }
            MaterialDemandTreeNodeDto root = buildRootForDemand(graph, demand);
            if (root == null) {
                continue;
            }
            roots.add(root);
            totalQty = totalQty.add(root.quantity());
            pathCount++;
        }

        roots.sort(Comparator.comparing(MaterialDemandTreeNodeDto::label));
        return new MaterialDemandDetailDto(materialCode, roots, totalQty, pathCount);
    }

    public List<MaterialDemandUsageDto> listDemandUsages(OntologyGraph graph, String materialCode) {
        List<MaterialDemandUsageDto> usages = new ArrayList<>();
        for (Demand demand : graph.demandsById().values()) {
            if (!materialCode.equals(demand.getProductCode())) {
                continue;
            }
            MaterialDemandUsageDto usage = toUsage(graph, demand);
            if (usage != null) {
                usages.add(usage);
            }
        }
        usages.sort(Comparator
                .comparing(MaterialDemandUsageDto::needDate)
                .thenComparing(MaterialDemandUsageDto::salesOrderNo)
                .thenComparingInt(MaterialDemandUsageDto::salesOrderLineNo)
                .thenComparing(MaterialDemandUsageDto::parentProductCode));
        return usages;
    }

    private MaterialDemandTreeNodeDto buildRootForDemand(OntologyGraph graph, Demand demand) {
        MaterialDemandTreeNodeDto materialNode = materialNode(demand);
        if (demand.getSourceType() == DemandSourceType.CUSTOMER_DELIVERY) {
            CustomerOrderLineDelivery delivery = graph.customerOrderLineDelivery(demand.getSourceId());
            if (delivery == null) {
                return materialNode;
            }
            CustomerOrderLine line = graph.customerOrderLine(delivery.getCustomerOrderLineId());
            if (line == null) {
                return materialNode;
            }
            return salesOrderRoot(line, delivery, List.of(materialNode));
        }
        if (demand.getSourceType() == DemandSourceType.BOM_COMPONENT) {
            SupplyOrder parent = graph.supplyOrder(demand.getSourceId());
            if (parent == null) {
                return materialNode;
            }
            MaterialDemandTreeNodeDto parentNode = new MaterialDemandTreeNodeDto(
                    "supo-" + parent.getId(),
                    "WORK_ORDER",
                    "工单 " + parent.getId(),
                    parent.getProductCode(),
                    parent.getNeedDate(),
                    BigDecimal.valueOf(parent.getQuantity()),
                    List.of(materialNode));
            CustomerOrderLine line = resolveCustomerLineForSupplyOrder(graph, parent.getId());
            if (line != null) {
                CustomerOrderLineDelivery delivery = graph.customerOrderLineDeliveriesById().values().stream()
                        .filter(d -> line.getId().equals(d.getCustomerOrderLineId()))
                        .findFirst()
                        .orElse(null);
                if (delivery != null) {
                    return salesOrderRoot(line, delivery, List.of(parentNode));
                }
            }
            return parentNode;
        }
        return materialNode;
    }

    private static MaterialDemandTreeNodeDto salesOrderRoot(
            CustomerOrderLine line,
            CustomerOrderLineDelivery delivery,
            List<MaterialDemandTreeNodeDto> children) {
        return new MaterialDemandTreeNodeDto(
                "so-" + line.getSalesOrderNo() + "-" + line.getSalesOrderLineNo(),
                "SALES_ORDER",
                line.getSalesOrderNo() + "-" + line.getSalesOrderLineNo(),
                line.getProductCode(),
                delivery.getLatestDesiredDate() != null ? delivery.getLatestDesiredDate() : delivery.getRequestedDate(),
                BigDecimal.valueOf(delivery.getDeliveryQty()),
                children);
    }

    private static MaterialDemandTreeNodeDto materialNode(Demand demand) {
        return new MaterialDemandTreeNodeDto(
                demand.getId(),
                "MATERIAL",
                "物料 " + demand.getProductCode(),
                demand.getProductCode(),
                demand.getNeedDate(),
                BigDecimal.valueOf(demand.getQuantity()),
                List.of());
    }

    private MaterialDemandUsageDto toUsage(OntologyGraph graph, Demand demand) {
        String salesOrderNo = "";
        int salesOrderLineNo = 0;
        String parentProductCode = "";
        String demanderLabel = demand.getId();
        String demandType = demand.getSourceType() != null ? demand.getSourceType().name() : "DEMAND";
        int bomLevel = 0;

        if (demand.getSourceType() == DemandSourceType.CUSTOMER_DELIVERY) {
            CustomerOrderLineDelivery delivery = graph.customerOrderLineDelivery(demand.getSourceId());
            if (delivery != null) {
                CustomerOrderLine line = graph.customerOrderLine(delivery.getCustomerOrderLineId());
                if (line != null) {
                    salesOrderNo = line.getSalesOrderNo();
                    salesOrderLineNo = line.getSalesOrderLineNo();
                    parentProductCode = line.getProductCode();
                    demanderLabel = salesOrderNo + "-" + salesOrderLineNo;
                }
            }
        } else if (demand.getSourceType() == DemandSourceType.BOM_COMPONENT) {
            SupplyOrder parent = graph.supplyOrder(demand.getSourceId());
            if (parent != null) {
                parentProductCode = parent.getProductCode();
                demanderLabel = "工单 " + parent.getId();
                bomLevel = 1;
                CustomerOrderLine line = resolveCustomerLineForSupplyOrder(graph, parent.getId());
                if (line != null) {
                    salesOrderNo = line.getSalesOrderNo();
                    salesOrderLineNo = line.getSalesOrderLineNo();
                }
            }
        }

        return new MaterialDemandUsageDto(
                demandType,
                demanderLabel,
                salesOrderNo,
                salesOrderLineNo,
                parentProductCode,
                demand.getNeedDate(),
                BigDecimal.valueOf(demand.getQuantity()),
                bomLevel);
    }

    private CustomerOrderLine resolveCustomerLineForSupplyOrder(OntologyGraph graph, String supplyOrderId) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(supplyOrderId);
        if (wo == null || wo.salesOrderNo == null || wo.salesOrderNo.isBlank()) {
            return null;
        }
        return graph.customerOrderLine(OntologyIds.customerOrderLineId(wo.salesOrderNo, wo.salesOrderLineNo));
    }
}
