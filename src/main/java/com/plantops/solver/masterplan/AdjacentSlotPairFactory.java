package com.plantops.solver.masterplan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class AdjacentSlotPairFactory {

    private AdjacentSlotPairFactory() {
    }

    public static List<AdjacentSlotPair> fromSlots(List<TimeSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        Map<String, List<TimeSlot>> byResource = slots.stream()
                .collect(Collectors.groupingBy(TimeSlot::getResourceId));
        List<AdjacentSlotPair> pairs = new ArrayList<>();
        for (List<TimeSlot> resourceSlots : byResource.values()) {
            resourceSlots.sort(Comparator.comparingInt(TimeSlot::getIndex));
            for (int i = 0; i + 1 < resourceSlots.size(); i++) {
                pairs.add(new AdjacentSlotPair(resourceSlots.get(i), resourceSlots.get(i + 1)));
            }
        }
        return pairs;
    }
}
