package com.plantops.api.dto.slitting;

public record CreateSlittingSessionRequest(String planVersionId, String activeParentNodeId) {
}
