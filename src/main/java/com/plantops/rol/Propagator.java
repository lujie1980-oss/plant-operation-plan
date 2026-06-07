package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;

final class Propagator {

    void propagate(DirtySet dirty, DerivationRegistry registry, OntologyGraph graph) {
        for (String targetKey : registry.topologicalOrder()) {
            if (!dirty.contains(targetKey)) {
                continue;
            }
            Derivation derivation = registry.derivation(targetKey);
            derivation.recompute().accept(graph, targetKey);
            dirty.clear(targetKey);
            for (Derivation dependent : registry.dependentsOf(targetKey)) {
                dirty.mark(dependent.targetKey());
            }
        }
    }
}
