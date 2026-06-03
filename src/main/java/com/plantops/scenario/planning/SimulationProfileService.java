package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.SimulationProfileDto;
import com.plantops.api.dto.planning.SaveSimulationProfileRequest;
import com.plantops.persistence.entity.SimulationProfileEntity;
import com.plantops.scenario.planning.simulation.SimulationProfileConfigParser;
import com.plantops.scenario.planning.simulation.SimulationProfileSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SimulationProfileService {

    public static final String LAYER_DETAIL_SCHEDULE = "DETAIL_SCHEDULE";
    public static final String DEFAULT_PROFILE_ID = "SP-DEFAULT";

    @Inject
    SimulationProfileConfigParser configParser;

    @Transactional
    public List<SimulationProfileDto> list() {
        ensureDefaultProfile();
        return SimulationProfileEntity.listInWorkspace().stream()
                .sorted(Comparator.comparing(e -> e.id))
                .map(this::toDto)
                .toList();
    }

    public SimulationProfileDto get(String profileId) {
        SimulationProfileEntity entity = require(profileId);
        return toDto(entity);
    }

    @Transactional
    public SimulationProfileDto save(SaveSimulationProfileRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("name required");
        }
        String configJson = request.configJson() != null && !request.configJson().isBlank()
                ? request.configJson()
                : SimulationProfileConfigParser.DEFAULT_CONFIG_JSON;
        configParser.parse(DEFAULT_PROFILE_ID, configJson);

        SimulationProfileEntity entity;
        if (request.profileId() != null && !request.profileId().isBlank()) {
            entity = SimulationProfileEntity.findByProfileId(request.profileId());
            if (entity == null) {
                entity = new SimulationProfileEntity();
                entity.profileId = request.profileId();
                entity.stampWorkspace();
            }
        } else {
            entity = new SimulationProfileEntity();
            entity.profileId = "SP-" + UUID.randomUUID().toString().substring(0, 8);
            entity.stampWorkspace();
        }
        entity.name = request.name().trim();
        entity.layer = request.layer() != null && !request.layer().isBlank()
                ? request.layer()
                : LAYER_DETAIL_SCHEDULE;
        entity.masterPlanVersionId = blankToNull(request.masterPlanVersionId());
        entity.configJson = configJson;
        if (request.active() != null) {
            if (request.active()) {
                deactivateOthers(entity.layer, entity.masterPlanVersionId, entity.profileId);
            }
            entity.active = request.active();
        }
        entity.updatedTs = LocalDateTime.now();
        entity.persist();
        return toDto(entity);
    }

    @Transactional
    public void delete(String profileId) {
        SimulationProfileEntity entity = require(profileId);
        if (DEFAULT_PROFILE_ID.equals(entity.profileId)) {
            throw new BadRequestException("Cannot delete default simulation profile");
        }
        entity.delete();
    }

    public SimulationProfileSnapshot resolveSnapshot(String masterPlanVersionId, String requestedProfileId) {
        ensureDefaultProfile();
        SimulationProfileEntity entity;
        if (requestedProfileId != null && !requestedProfileId.isBlank()) {
            entity = require(requestedProfileId);
        } else {
            entity = SimulationProfileEntity.findActiveForLayer(LAYER_DETAIL_SCHEDULE, masterPlanVersionId);
            if (entity == null) {
                entity = require(DEFAULT_PROFILE_ID);
            }
        }
        return new SimulationProfileSnapshot(entity.profileId, entity.configJson);
    }

    @Transactional
    public void ensureDefaultProfile() {
        if (SimulationProfileEntity.findByProfileId(DEFAULT_PROFILE_ID) != null) {
            return;
        }
        SimulationProfileEntity entity = new SimulationProfileEntity();
        entity.stampWorkspace();
        entity.profileId = DEFAULT_PROFILE_ID;
        entity.name = "默认详细排程推演";
        entity.layer = LAYER_DETAIL_SCHEDULE;
        entity.configJson = SimulationProfileConfigParser.DEFAULT_CONFIG_JSON;
        entity.active = true;
        entity.updatedTs = LocalDateTime.now();
        entity.persist();
    }

    private void deactivateOthers(String layer, String masterPlanVersionId, String keepProfileId) {
        for (SimulationProfileEntity other : SimulationProfileEntity.listInWorkspace()) {
            if (!layer.equals(other.layer)) {
                continue;
            }
            if (!sameScope(other.masterPlanVersionId, masterPlanVersionId)) {
                continue;
            }
            if (keepProfileId.equals(other.profileId)) {
                continue;
            }
            other.active = false;
            other.updatedTs = LocalDateTime.now();
        }
    }

    private static boolean sameScope(String left, String right) {
        String a = blankToNull(left);
        String b = blankToNull(right);
        if (a == null && b == null) {
            return true;
        }
        return a != null && a.equals(b);
    }

    private SimulationProfileEntity require(String profileId) {
        SimulationProfileEntity entity = SimulationProfileEntity.findByProfileId(profileId);
        if (entity == null) {
            throw new NotFoundException("Unknown simulation profile: " + profileId);
        }
        return entity;
    }

    private SimulationProfileDto toDto(SimulationProfileEntity entity) {
        return new SimulationProfileDto(
                entity.profileId,
                entity.name,
                entity.layer,
                entity.masterPlanVersionId,
                entity.configJson,
                entity.active,
                entity.updatedTs);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
