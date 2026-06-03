package com.plantops.scenario.planning.simulation;

import com.plantops.scenario.FeedbackFreezeIndex;
import com.plantops.persistence.entity.SimulationProfileEntity;
import com.plantops.scenario.planning.SimulationProfileService;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.DetailScheduleProblemFacts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class SimulationProfileResolver {

    @Inject
    SimulationProfileConfigParser configParser;

    @Inject
    SimulationProfileService profileService;

    public SimulationRuleContext buildContext(
            DetailSchedule schedule,
            SimulationMode mode,
            Set<String> seedOperationIds,
            SimulationProfileSnapshot sessionSnapshot,
            String requestProfileId,
            Map<String, Map<String, Object>> requestRuleOverrides,
            LocalDate feedbackCutoff) {
        SimulationProfileSnapshot effective = resolveEffectiveSnapshot(sessionSnapshot, requestProfileId);
        SimulationProfileSettings settings = configParser.parse(
                effective != null ? effective.profileId() : null,
                effective != null ? effective.configJson() : SimulationProfileConfigParser.DEFAULT_CONFIG_JSON);
        settings = configParser.mergeOverrides(settings, requestRuleOverrides);

        DetailScheduleProblemFacts facts = schedule != null ? schedule.getProblemFacts() : null;
        LocalDate anchor = facts != null && facts.planningAnchorDate() != null
                ? facts.planningAnchorDate()
                : LocalDate.now();
        if (schedule != null && feedbackCutoff != null) {
            facts = mergeFeedbackFreeze(facts, anchor, feedbackCutoff);
            schedule.setProblemFacts(facts);
        }
        return new SimulationRuleContext(
                schedule,
                facts,
                null,
                Map.of(),
                mode,
                seedOperationIds != null ? seedOperationIds : Set.of(),
                anchor,
                settings);
    }

    public SimulationRuleContext buildContext(
            DetailSchedule schedule,
            SimulationMode mode,
            Set<String> seedOperationIds,
            SimulationProfileSnapshot sessionSnapshot,
            String requestProfileId,
            Map<String, Map<String, Object>> requestRuleOverrides) {
        return buildContext(
                schedule, mode, seedOperationIds, sessionSnapshot, requestProfileId, requestRuleOverrides, null);
    }

    private static DetailScheduleProblemFacts mergeFeedbackFreeze(
            DetailScheduleProblemFacts facts,
            LocalDate anchor,
            LocalDate feedbackCutoff) {
        if (facts == null) {
            return new DetailScheduleProblemFacts(
                    null,
                    anchor,
                    null,
                    null,
                    null,
                    FeedbackFreezeIndex.fromWorkspace(anchor, feedbackCutoff));
        }
        return new DetailScheduleProblemFacts(
                facts.contractSettings(),
                facts.planningAnchorDate(),
                facts.changeoverRules(),
                facts.transferRules(),
                facts.workingCalendar(),
                FeedbackFreezeIndex.fromWorkspace(anchor, feedbackCutoff));
    }

    private SimulationProfileSnapshot resolveEffectiveSnapshot(
            SimulationProfileSnapshot sessionSnapshot,
            String requestProfileId) {
        if (requestProfileId != null && !requestProfileId.isBlank()) {
            SimulationProfileEntity entity = SimulationProfileEntity.findByProfileId(requestProfileId);
            if (entity == null) {
                throw new NotFoundException("Unknown simulation profile: " + requestProfileId);
            }
            return new SimulationProfileSnapshot(entity.profileId, entity.configJson);
        }
        if (sessionSnapshot != null) {
            return sessionSnapshot;
        }
        profileService.ensureDefaultProfile();
        SimulationProfileEntity active = SimulationProfileEntity.findActiveForLayer(
                SimulationProfileService.LAYER_DETAIL_SCHEDULE, null);
        if (active == null) {
            active = SimulationProfileEntity.findByProfileId(SimulationProfileService.DEFAULT_PROFILE_ID);
        }
        return active != null
                ? new SimulationProfileSnapshot(active.profileId, active.configJson)
                : new SimulationProfileSnapshot(
                        SimulationProfileService.DEFAULT_PROFILE_ID,
                        SimulationProfileConfigParser.DEFAULT_CONFIG_JSON);
    }
}
