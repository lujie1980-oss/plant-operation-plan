package com.plantops.rol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DerivationRegistry {

    private final List<Derivation> derivations;
    private final Map<String, Derivation> byTargetKey;
    private final Map<String, List<Derivation>> dependentsByDependencyKey;
    private final List<String> topologicalOrder;

    public DerivationRegistry(List<Derivation> derivations) {
        this.derivations = List.copyOf(derivations);
        this.byTargetKey = new HashMap<>();
        this.dependentsByDependencyKey = new HashMap<>();
        for (Derivation derivation : derivations) {
            if (byTargetKey.putIfAbsent(derivation.targetKey(), derivation) != null) {
                throw new IllegalStateException("Duplicate derivation target: " + derivation.targetKey());
            }
            for (String dependency : derivation.dependencies()) {
                dependentsByDependencyKey
                        .computeIfAbsent(dependency, ignored -> new ArrayList<>())
                        .add(derivation);
            }
        }
        Map<String, Set<String>> edges = new HashMap<>();
        for (Derivation derivation : derivations) {
            edges.put(derivation.targetKey(), derivation.dependencies());
        }
        this.topologicalOrder = DependencyGraph.buildTopologicalOrder(edges);
    }

    public List<Derivation> derivations() {
        return derivations;
    }

    public Derivation derivation(String targetKey) {
        return byTargetKey.get(targetKey);
    }

    public List<Derivation> dependentsOf(String dependencyKey) {
        List<Derivation> dependents = dependentsByDependencyKey.get(dependencyKey);
        return dependents == null ? List.of() : Collections.unmodifiableList(dependents);
    }

    public List<String> topologicalOrder() {
        return topologicalOrder;
    }
}
