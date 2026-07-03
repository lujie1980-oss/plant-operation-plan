package com.plantops.integration.adapter;

import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterRunResultDto;
import com.plantops.integration.IntegrationAdapterPort;
import com.plantops.integration.mes.MesPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** ADP-MES：轮询 MES 反馈（mock · API-INT-05）。 */
@ApplicationScoped
public class MesIntegrationAdapter implements IntegrationAdapterPort {

    @Inject
    MesPort mesPort;

    @Override
    public String adapterId() {
        return "ADP-MES";
    }

    @Override
    public String sourceSystemCode() {
        return "MES_DEFAULT";
    }

    @Override
    public IntegrationAdapterRunResultDto run(boolean validateOnly) {
        Object feedback = mesPort.pollFeedback();
        if (validateOnly) {
            return new IntegrationAdapterRunResultDto(null, "SUCCESS", "MES 连接可用");
        }
        return new IntegrationAdapterRunResultDto(
                null, "SUCCESS", "MES 轮询完成：" + (feedback != null ? feedback.toString() : "无反馈"));
    }
}
