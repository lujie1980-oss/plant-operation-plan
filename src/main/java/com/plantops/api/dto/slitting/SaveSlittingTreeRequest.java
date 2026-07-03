package com.plantops.api.dto.slitting;

import java.util.List;

public record SaveSlittingTreeRequest(List<SlittingRollNodeDto> nodes, List<SlittingAssignmentDto> assignments) {
}
