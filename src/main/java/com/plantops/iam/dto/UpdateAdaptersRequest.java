package com.plantops.iam.dto;

import java.util.List;

public record UpdateAdaptersRequest(List<AdapterToggleRequest> adapters) {
    public record AdapterToggleRequest(String adapterId, boolean enabled) {}
}
