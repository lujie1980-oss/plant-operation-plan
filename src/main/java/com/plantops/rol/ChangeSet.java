package com.plantops.rol;

import java.util.List;

public record ChangeSet(List<ChangeOperation> operations, String description) {

    public ChangeSet {
        operations = operations == null ? List.of() : List.copyOf(operations);
    }

    public ChangeSet(List<ChangeOperation> operations) {
        this(operations, null);
    }
}
