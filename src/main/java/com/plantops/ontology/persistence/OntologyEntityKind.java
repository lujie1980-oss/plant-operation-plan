package com.plantops.ontology.persistence;

/** P0 entity kinds governed by {@code ont_entity_policy} (§5.16). */
public enum OntologyEntityKind {
    DEMAND,
    SUPPLY_ORDER,
    OPERATION,
    FULFILLMENT,
    PISPP,
    PERIOD,
    PRP,
    SRP,
    RESOURCE_CAPACITY_ASSIGNMENT,
    BOM_DEPENDENCY
}
