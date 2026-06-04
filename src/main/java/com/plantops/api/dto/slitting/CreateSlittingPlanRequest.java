package com.plantops.api.dto.slitting;

import java.util.List;

public record CreateSlittingPlanRequest(String name, List<String> masterRollCodes, List<String> childOrderCodes) {
}
