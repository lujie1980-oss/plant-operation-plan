package com.plantops.api.dto.slitting;

import java.util.List;

public record PatchSlittingSessionRequest(List<SlittingAssignmentPatchDto> assignmentPatches) {
}
