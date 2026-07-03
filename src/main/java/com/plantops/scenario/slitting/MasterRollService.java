package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.MasterRollDto;
import com.plantops.persistence.entity.MasterRollEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class MasterRollService {

    public List<MasterRollDto> list() {
        return MasterRollEntity.listInWorkspace().stream().map(MasterRollService::toDto).toList();
    }

    @Transactional
    public MasterRollDto create(MasterRollDto dto) {
        validate(dto);
        if (MasterRollEntity.findByRollCode(dto.rollCode()) != null) {
            throw new BadRequestException("rollCode already exists: " + dto.rollCode());
        }
        MasterRollEntity entity = new MasterRollEntity();
        entity.stampWorkspace();
        apply(entity, dto);
        entity.persist();
        return toDto(entity);
    }

    @Transactional
    public MasterRollDto update(String rollCode, MasterRollDto dto) {
        validate(dto);
        MasterRollEntity entity = require(rollCode);
        apply(entity, dto);
        return toDto(entity);
    }

    @Transactional
    public void archive(String rollCode) {
        MasterRollEntity entity = require(rollCode);
        entity.status = MasterRollEntity.STATUS_ARCHIVED;
    }

    public MasterRollEntity requireEntity(String rollCode) {
        return require(rollCode);
    }

    private static MasterRollEntity require(String rollCode) {
        MasterRollEntity entity = MasterRollEntity.findByRollCode(rollCode);
        if (entity == null) {
            throw new NotFoundException("master roll not found: " + rollCode);
        }
        return entity;
    }

    private static void validate(MasterRollDto dto) {
        if (dto.rollCode() == null || dto.rollCode().isBlank()) {
            throw new BadRequestException("rollCode required");
        }
        if (dto.widthMm() == null || dto.widthMm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("widthMm must be positive");
        }
        if (dto.lengthMm() == null || dto.lengthMm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("lengthMm must be positive");
        }
    }

    private static void apply(MasterRollEntity entity, MasterRollDto dto) {
        entity.rollCode = dto.rollCode();
        entity.widthMm = dto.widthMm();
        entity.lengthMm = dto.lengthMm();
        entity.thicknessMm = dto.thicknessMm();
        entity.materialCode = dto.materialCode();
        entity.productCode = dto.productCode();
        entity.finishedProductCode = dto.finishedProductCode() != null
                ? dto.finishedProductCode()
                : dto.productCode();
        entity.kerfLongitudinalMm = dto.kerfLongitudinalMm() != null ? dto.kerfLongitudinalMm() : BigDecimal.ZERO;
        entity.kerfTransverseMm = dto.kerfTransverseMm() != null ? dto.kerfTransverseMm() : BigDecimal.ZERO;
        entity.status = dto.status() != null ? dto.status() : MasterRollEntity.STATUS_AVAILABLE;
    }

    static MasterRollDto toDto(MasterRollEntity entity) {
        return new MasterRollDto(
                entity.rollCode,
                entity.widthMm,
                entity.lengthMm,
                entity.thicknessMm,
                entity.materialCode,
                entity.productCode,
                entity.finishedProductCode,
                entity.kerfLongitudinalMm,
                entity.kerfTransverseMm,
                entity.status);
    }
}
