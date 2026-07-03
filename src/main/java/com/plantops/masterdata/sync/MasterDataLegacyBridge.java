package com.plantops.masterdata.sync;

import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.MdPispEntity;
import com.plantops.persistence.entity.MdPhysicalResourceEntity;
import com.plantops.persistence.entity.MdRoutingEntity;
import com.plantops.persistence.entity.MdRoutingStepEntity;
import com.plantops.persistence.entity.MdRoutingStepImEntity;
import com.plantops.persistence.entity.MdRoutingStepOsrEntity;
import com.plantops.persistence.entity.MdStandardResourceEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * §11 legacy 过渡：md_* sync 后 upsert legacy 表（已退役 · TODO-13 M5）。
 *
 * @deprecated M5 后主计划读路径仅使用 md_*；保留类供历史对照与手工迁移。
 */
@Deprecated
@ApplicationScoped
public class MasterDataLegacyBridge {

    @Transactional
    public void syncFromMd() {
        syncMaterials();
        syncProductionResources();
        syncProductionLines();
        syncProductResources();
        syncBomFromFirstStepInputs();
    }

    private void syncMaterials() {
        for (MdPispEntity pisp : MdPispEntity.listInWorkspace()) {
            if (MaterialEntity.findByCode(pisp.productCode) != null) {
                continue;
            }
            MaterialEntity material = new MaterialEntity();
            material.materialCode = pisp.productCode;
            material.materialName = pisp.productCode;
            material.materialType = "成品";
            material.stampWorkspace();
            material.persist();
        }
    }

    private void syncProductionResources() {
        for (MdStandardResourceEntity sr : MdStandardResourceEntity.listInWorkspace()) {
            ProductionResourceEntity existing = ProductionResourceEntity.findByResourceId(sr.code);
            if (existing != null) {
                existing.resourceGroup = sr.resourceGroupCode;
                existing.bottleneck = sr.bottleneck;
                if (sr.resourceEfficiency != null) {
                    existing.resourceEfficiency = sr.resourceEfficiency;
                }
                continue;
            }
            ProductionResourceEntity resource = new ProductionResourceEntity();
            resource.resourceId = sr.code;
            resource.resourceGroup = sr.resourceGroupCode;
            resource.areaId = "AREA-MD";
            resource.bottleneck = sr.bottleneck;
            resource.resourceEfficiency = sr.resourceEfficiency != null ? sr.resourceEfficiency : BigDecimal.ONE;
            resource.stampWorkspace();
            resource.persist();
        }
    }

    private void syncProductionLines() {
        for (MdPhysicalResourceEntity pr : MdPhysicalResourceEntity.listInWorkspace()) {
            if (pr.productionLineCode == null || pr.productionLineCode.isBlank()) {
                continue;
            }
            ProductionLineEntity line = ProductionLineEntity.findByLineId(pr.productionLineCode);
            if (line == null) {
                line = new ProductionLineEntity();
                line.lineId = pr.productionLineCode;
                line.areaId = "AREA-MD";
                line.resourceId = pr.standardResourceCode;
                line.lineMinHeadcount = 1;
                line.lineCapacityPerShift = 480;
                line.stampWorkspace();
                line.persist();
            } else if (line.resourceId == null || line.resourceId.isBlank()) {
                line.resourceId = pr.standardResourceCode;
            }
        }
    }

    private void syncProductResources() {
        Map<String, MdRoutingEntity> routingByCode =
                MdRoutingEntity.listInWorkspace().stream()
                        .collect(Collectors.toMap(r -> r.routingCode, r -> r, (a, b) -> a));
        for (MdRoutingStepOsrEntity osr : MdRoutingStepOsrEntity.listInWorkspace()) {
            MdRoutingEntity routing = routingByCode.get(osr.routingCode);
            if (routing == null) {
                continue;
            }
            MdRoutingStepEntity step = MdRoutingStepEntity.find(
                            "workspaceId = ?1 and routingCode = ?2 and sequenceNo = ?3",
                            MdRoutingStepEntity.ws(),
                            osr.routingCode,
                            osr.sequenceNo)
                    .firstResult();
            if (step == null) {
                continue;
            }
            ProductResourceEntity existing =
                    ProductResourceEntity.findByProductAndResource(routing.productCode, osr.standardResourceCode);
            if (existing != null) {
                existing.sequenceNo = osr.sequenceNo;
                existing.resourcePriority = osr.resourcePriority;
                existing.routingPathPriority = routing.pathPriority;
                existing.operationName = step.operationName;
                existing.setupTimeMinutes = osr.setupTimeMinutes;
                existing.processTimeSeconds = osr.processTimeSeconds;
                continue;
            }
            ProductResourceEntity pr = new ProductResourceEntity();
            pr.productCode = routing.productCode;
            pr.resourceId = osr.standardResourceCode;
            pr.sequenceNo = osr.sequenceNo;
            pr.resourcePriority = osr.resourcePriority;
            pr.routingPathPriority = routing.pathPriority;
            pr.operationName = step.operationName;
            pr.setupTimeMinutes = osr.setupTimeMinutes;
            pr.processTimeSeconds =
                    osr.processTimeSeconds != null ? osr.processTimeSeconds : BigDecimal.valueOf(60);
            pr.stampWorkspace();
            pr.persist();
        }
    }

    private void syncBomFromFirstStepInputs() {
        Map<String, MdRoutingEntity> routingByCode =
                MdRoutingEntity.listInWorkspace().stream()
                        .collect(Collectors.toMap(r -> r.routingCode, r -> r, (a, b) -> a));
        Map<String, Integer> minSeqByRouting =
                MdRoutingStepEntity.listInWorkspace().stream()
                        .collect(Collectors.groupingBy(
                                s -> s.routingCode,
                                Collectors.collectingAndThen(
                                        Collectors.minBy(Comparator.comparingInt(s -> s.sequenceNo)),
                                        opt -> opt.map(s -> s.sequenceNo).orElse(1))));

        for (MdRoutingStepImEntity im : MdRoutingStepImEntity.listInWorkspace()) {
            Integer minSeq = minSeqByRouting.get(im.routingCode);
            if (minSeq == null || im.sequenceNo != minSeq) {
                continue;
            }
            MdRoutingEntity routing = routingByCode.get(im.routingCode);
            if (routing == null) {
                continue;
            }
            List<BomComponentEntity> existing =
                    BomComponentEntity.findChildren(routing.productCode, routing.productCode);
            boolean already = existing.stream()
                    .anyMatch(b -> im.componentProductCode.equals(b.componentProductCode));
            if (already) {
                continue;
            }
            BomComponentEntity bom = new BomComponentEntity();
            bom.finishedProductCode = routing.productCode;
            bom.parentProductCode = routing.productCode;
            bom.componentProductCode = im.componentProductCode;
            bom.componentQty = im.componentQty != null ? im.componentQty : BigDecimal.ONE;
            bom.bomId = "MD-" + routing.productCode;
            bom.bomVersion = "1";
            bom.stampWorkspace();
            bom.persist();
        }
    }
}
