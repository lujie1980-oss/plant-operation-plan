package com.plantops.ontology.master;

/** ENT-PR · 物理产线/设备，N:1 映射 ENT-SR（ADR-17 · RULE-MD-12）。 */
public final class PhysicalResource {

    private final String id;
    private final String standardResourceId;

    public PhysicalResource(String id, String standardResourceId) {
        this.id = id;
        this.standardResourceId = standardResourceId;
    }

    public String getId() {
        return id;
    }

    public String getStandardResourceId() {
        return standardResourceId;
    }
}
