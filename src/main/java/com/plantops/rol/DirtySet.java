package com.plantops.rol;

import java.util.LinkedHashSet;
import java.util.Set;

final class DirtySet {

    private final Set<String> dirtyTargetKeys = new LinkedHashSet<>();

    void mark(String targetKey) {
        dirtyTargetKeys.add(targetKey);
    }

    boolean contains(String targetKey) {
        return dirtyTargetKeys.contains(targetKey);
    }

    void clear(String targetKey) {
        dirtyTargetKeys.remove(targetKey);
    }

    boolean isEmpty() {
        return dirtyTargetKeys.isEmpty();
    }
}
