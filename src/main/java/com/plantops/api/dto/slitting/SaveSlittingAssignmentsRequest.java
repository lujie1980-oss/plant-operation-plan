package com.plantops.api.dto.slitting;

import java.util.List;

public record SaveSlittingAssignmentsRequest(List<SlittingAssignmentDto> assignments) {
}
