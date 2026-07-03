package com.plantops.api.dto.slitting;

import java.util.List;

public record ImportChildOrdersFromDemandResult(int created, int skipped, List<ChildSlittingOrderDto> orders) {
}
