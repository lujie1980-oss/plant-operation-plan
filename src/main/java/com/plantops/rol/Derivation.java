package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;

import java.util.Set;
import java.util.function.BiConsumer;

public record Derivation(
        String targetKey,
        Set<String> dependencies,
        BiConsumer<OntologyGraph, String> recompute) {

    public static String propertyKey(String pisppId, String property) {
        return pisppId + "#" + property;
    }
}
