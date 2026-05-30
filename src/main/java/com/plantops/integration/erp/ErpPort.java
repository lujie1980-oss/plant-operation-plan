package com.plantops.integration.erp;

import com.plantops.api.dto.DemandPoolEntryDto;

import java.util.List;

public interface ErpPort {
    List<DemandPoolEntryDto> fetchOpenOrderLines();
}
