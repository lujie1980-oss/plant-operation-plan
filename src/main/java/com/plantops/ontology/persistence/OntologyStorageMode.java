package com.plantops.ontology.persistence;

/** STORE = persist in ont_*; DERIVE = skip write, recompute on load (§5.16). */
public enum OntologyStorageMode {
    STORE,
    DERIVE;

    public boolean stores() {
        return this == STORE;
    }
}
