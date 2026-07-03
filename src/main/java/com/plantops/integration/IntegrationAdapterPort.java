package com.plantops.integration;

import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterRunResultDto;

/** ADP-* 适配器 SPI（TODO-19 · API-INT-05/07）。 */
public interface IntegrationAdapterPort {

    String adapterId();

    String sourceSystemCode();

    IntegrationAdapterRunResultDto run(boolean validateOnly);
}
