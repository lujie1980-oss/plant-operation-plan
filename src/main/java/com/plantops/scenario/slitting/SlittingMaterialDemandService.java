package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.SlittingMaterialDemandDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class SlittingMaterialDemandService {

    public List<SlittingMaterialDemandDto> demandsForMaterial(String materialProductCode, String finishedProductCode) {
        if (materialProductCode == null || materialProductCode.isBlank()) {
            return List.of();
        }
        String material = materialProductCode.trim();
        List<SlittingMaterialDemandDto> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ChildSlittingOrderEntity order : ChildSlittingOrderEntity.listInWorkspace()) {
            if (material.equals(order.productCode)) {
                add(
                        out,
                        seen,
                        "CHILD_SLITTING",
                        order.orderCode,
                        "分切订单 " + order.orderCode,
                        order.productCode,
                        order.finishedProductCode,
                        BigDecimal.valueOf(order.quantity),
                        order.salesOrderNo,
                        order.salesOrderLineNo,
                        "直接匹配分切料号");
            }
        }

        for (SalesOrderLineEntity so : SalesOrderLineEntity.listInWorkspace()) {
            if (material.equals(so.productCode)) {
                add(
                        out,
                        seen,
                        "SALES_ORDER",
                        so.salesOrderNo + "-" + so.salesOrderLineNo,
                        "销售订单 " + so.salesOrderNo,
                        so.productCode,
                        so.productCode,
                        so.orderQty,
                        so.salesOrderNo,
                        so.salesOrderLineNo,
                        "销售订单行料号");
            }
        }

        String scopeFinished = finishedProductCode != null && !finishedProductCode.isBlank()
                ? finishedProductCode.trim()
                : null;
        for (BomComponentEntity bom : BomComponentEntity.listInWorkspace()) {
            if (!material.equals(bom.componentProductCode)) {
                continue;
            }
            String fg = bom.finishedProductCode != null ? bom.finishedProductCode : bom.parentProductCode;
            if (scopeFinished != null && !scopeFinished.equals(fg)) {
                continue;
            }
            for (SalesOrderLineEntity so : SalesOrderLineEntity.listInWorkspace()) {
                if (!fg.equals(so.productCode) && !isDescendantDemand(so.productCode, fg)) {
                    continue;
                }
                String key = "SO-BOM:" + so.salesOrderNo + ":" + so.salesOrderLineNo;
                add(
                        out,
                        seen,
                        "SALES_ORDER",
                        key,
                        "销售订单 " + so.salesOrderNo + "（BOM 用料）",
                        material,
                        fg,
                        so.orderQty,
                        so.salesOrderNo,
                        so.salesOrderLineNo,
                        "BOM 子件 → 成品 " + fg);
            }
            for (ChildSlittingOrderEntity order : ChildSlittingOrderEntity.listInWorkspace()) {
                String ofg = order.finishedProductCode != null ? order.finishedProductCode : order.productCode;
                if (scopeFinished != null && !scopeFinished.equals(ofg) && !fg.equals(ofg)) {
                    continue;
                }
                if (fg.equals(ofg) || fg.equals(order.productCode)) {
                    add(
                            out,
                            seen,
                            "CHILD_SLITTING",
                            order.orderCode + "-BOM",
                            "分切订单 " + order.orderCode + "（BOM 用料）",
                            material,
                            fg,
                            BigDecimal.valueOf(order.quantity),
                            order.salesOrderNo,
                            order.salesOrderLineNo,
                            "BOM 子件 → " + fg);
                }
            }
        }

        return out;
    }

    private static boolean isDescendantDemand(String soProduct, String finishedRoot) {
        if (soProduct == null || finishedRoot == null) {
            return false;
        }
        if (soProduct.equals(finishedRoot)) {
            return true;
        }
        return BomComponentEntity.findChildren(finishedRoot, finishedRoot).stream()
                .anyMatch(b -> soProduct.equals(b.componentProductCode) || soProduct.equals(b.parentProductCode));
    }

    private static void add(
            List<SlittingMaterialDemandDto> out,
            Set<String> seen,
            String type,
            String id,
            String label,
            String productCode,
            String finished,
            BigDecimal qty,
            String soNo,
            Integer soLine,
            String relation) {
        String key = type + ":" + id;
        if (!seen.add(key)) {
            return;
        }
        out.add(
                new SlittingMaterialDemandDto(
                        type, id, label, productCode, finished, qty, soNo, soLine, relation));
    }
}
