package com.plantops.integration.erp;

import com.plantops.api.dto.DemandPoolEntryDto;
import com.plantops.scenario.DemandService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class MockErpAdapter implements ErpPort {

    @Inject
    DemandService demandService;

    @Override
    public List<DemandPoolEntryDto> fetchOpenOrderLines() {
        return demandService.getDemandPool();
    }
}
