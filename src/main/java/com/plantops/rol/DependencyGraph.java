package com.plantops.rol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DependencyGraph {

    private DependencyGraph() {
    }

    static List<String> buildTopologicalOrder(Map<String, Set<String>> targetToDependencies) {
        Set<String> allTargets = targetToDependencies.keySet();
        Map<String, Set<String>> dependents = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();

        for (String target : allTargets) {
            indegree.put(target, 0);
        }

        for (Map.Entry<String, Set<String>> entry : targetToDependencies.entrySet()) {
            String target = entry.getKey();
            for (String dependency : entry.getValue()) {
                if (!allTargets.contains(dependency)) {
                    continue;
                }
                dependents.computeIfAbsent(dependency, ignored -> new HashSet<>()).add(target);
                indegree.merge(target, 1, Integer::sum);
            }
        }

        Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String node = ready.removeFirst();
            order.add(node);
            for (String dependent : dependents.getOrDefault(node, Set.of())) {
                int next = indegree.merge(dependent, -1, Integer::sum);
                if (next == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (order.size() != allTargets.size()) {
            throw new IllegalStateException("Derivation dependency cycle detected");
        }
        return List.copyOf(order);
    }
}
