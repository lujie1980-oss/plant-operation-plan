package com.plantops.ontology.period;

import com.plantops.ontology.master.PhysicalResource;
import com.plantops.persistence.entity.ProductionLineEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从产线主数据投影 ENT-PR；无产线时 1:1 合成 PR（ADR-17 P1）。 */
public final class PhysicalResourceRegistry {

    private final Map<String, PhysicalResource> byId;
    private final Map<String, List<PhysicalResource>> byStandardResourceId;

    private PhysicalResourceRegistry(
            Map<String, PhysicalResource> byId, Map<String, List<PhysicalResource>> byStandardResourceId) {
        this.byId = byId;
        this.byStandardResourceId = byStandardResourceId;
    }

    public static PhysicalResourceRegistry forWorkspace(Set<String> standardResourceIds) {
        Map<String, PhysicalResource> byId = new LinkedHashMap<>();
        Map<String, List<PhysicalResource>> bySr = new LinkedHashMap<>();

        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.lineId == null || line.lineId.isBlank()) {
                continue;
            }
            if (line.resourceId == null || line.resourceId.isBlank()) {
                continue;
            }
            PhysicalResource pr = new PhysicalResource(line.lineId, line.resourceId);
            byId.putIfAbsent(pr.getId(), pr);
            bySr.computeIfAbsent(line.resourceId, ignored -> new ArrayList<>()).add(pr);
        }

        for (String srId : standardResourceIds) {
            List<PhysicalResource> existing = bySr.get(srId);
            if (existing == null || existing.isEmpty()) {
                PhysicalResource synthetic = new PhysicalResource(srId, srId);
                byId.putIfAbsent(synthetic.getId(), synthetic);
                bySr.computeIfAbsent(srId, ignored -> new ArrayList<>()).add(synthetic);
            }
        }

        return new PhysicalResourceRegistry(byId, bySr);
    }

    /** 单元测试用：不依赖 JPA 产线主数据。 */
    static PhysicalResourceRegistry forPhysicalResources(
            List<PhysicalResource> resources, Set<String> standardResourceIds) {
        Map<String, PhysicalResource> byId = new LinkedHashMap<>();
        Map<String, List<PhysicalResource>> bySr = new LinkedHashMap<>();
        for (PhysicalResource pr : resources) {
            byId.putIfAbsent(pr.getId(), pr);
            bySr.computeIfAbsent(pr.getStandardResourceId(), ignored -> new ArrayList<>()).add(pr);
        }
        for (String srId : standardResourceIds) {
            if (!bySr.containsKey(srId) || bySr.get(srId).isEmpty()) {
                PhysicalResource synthetic = new PhysicalResource(srId, srId);
                byId.putIfAbsent(synthetic.getId(), synthetic);
                bySr.computeIfAbsent(srId, ignored -> new ArrayList<>()).add(synthetic);
            }
        }
        return new PhysicalResourceRegistry(byId, bySr);
    }

    public boolean isPhysicalResource(String id) {
        return byId.containsKey(id);
    }

    public boolean isStandardResource(String id) {
        return byStandardResourceId.containsKey(id);
    }

    public String standardResourceForPhysical(String physicalResourceId) {
        PhysicalResource pr = byId.get(physicalResourceId);
        return pr != null ? pr.getStandardResourceId() : null;
    }

    public List<PhysicalResource> physicalResourcesForStandard(String standardResourceId) {
        return byStandardResourceId.getOrDefault(standardResourceId, List.of());
    }

    public Collection<PhysicalResource> all() {
        return byId.values();
    }
}
