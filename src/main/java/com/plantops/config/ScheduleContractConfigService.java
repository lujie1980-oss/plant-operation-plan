package com.plantops.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.solver.detailschedule.ScheduleContractPenaltyMode;
import com.plantops.solver.detailschedule.ScheduleContractSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ScheduleContractConfigService {

    public static final String PARAM_ID = "detail_schedule_contract";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String DEFAULT_CONTRACT_JSON =
            "{\"weight_due\":100,\"weight_mp_late\":20,\"weight_mp_early\":60,"
                    + "\"mp_late_mode\":\"LINEAR\",\"mp_early_mode\":\"QUADRATIC\",\"mp_early_cap_days\":0,"
                    + "\"enable_mp_target\":false,\"enable_mp_contract_start_wait\":true}";

    @Inject
    ParameterRegistry parameters;

    public ScheduleContractSettings load() {
        String raw = parameters.get(PARAM_ID);
        if (raw == null || raw.isBlank()) {
            return ScheduleContractSettings.defaults();
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            int weightDue = root.path("weight_due").asInt(100);
            int weightMpLate = root.path("weight_mp_late").asInt(20);
            int weightMpEarly = root.path("weight_mp_early").asInt(60);
            ScheduleContractPenaltyMode lateMode = parseMode(root.path("mp_late_mode").asText("LINEAR"));
            ScheduleContractPenaltyMode earlyMode = parseMode(root.path("mp_early_mode").asText("QUADRATIC"));
            int earlyCap = root.path("mp_early_cap_days").asInt(0);
            boolean enableMpTarget = root.path("enable_mp_target").asBoolean(false);
            boolean enableMpContractStartWait = root.path("enable_mp_contract_start_wait").asBoolean(true);
            return new ScheduleContractSettings(
                    weightDue,
                    weightMpLate,
                    weightMpEarly,
                    lateMode,
                    earlyMode,
                    earlyCap,
                    enableMpTarget,
                    enableMpContractStartWait);
        } catch (Exception e) {
            return ScheduleContractSettings.defaults();
        }
    }

    private static ScheduleContractPenaltyMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return ScheduleContractPenaltyMode.LINEAR;
        }
        return ScheduleContractPenaltyMode.valueOf(raw.trim().toUpperCase());
    }
}
