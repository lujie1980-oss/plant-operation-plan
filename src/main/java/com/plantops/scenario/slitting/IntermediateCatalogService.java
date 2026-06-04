package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.IntermediateRollCatalogDto;
import com.plantops.persistence.entity.IntermediateRollCatalogEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class IntermediateCatalogService {

    public List<IntermediateRollCatalogDto> list() {
        return IntermediateRollCatalogEntity.listInWorkspace().stream().map(IntermediateCatalogService::toDto).toList();
    }

    @Transactional
    public IntermediateRollCatalogDto create(IntermediateRollCatalogDto dto) {
        validate(dto);
        if (IntermediateRollCatalogEntity.findBySpecCode(dto.specCode()) != null) {
            throw new BadRequestException("specCode already exists: " + dto.specCode());
        }
        IntermediateRollCatalogEntity entity = new IntermediateRollCatalogEntity();
        entity.stampWorkspace();
        apply(entity, dto);
        entity.persist();
        return toDto(entity);
    }

    @Transactional
    public IntermediateRollCatalogDto update(String specCode, IntermediateRollCatalogDto dto) {
        validate(dto);
        IntermediateRollCatalogEntity entity = require(specCode);
        apply(entity, dto);
        return toDto(entity);
    }

    @Transactional
    public void delete(String specCode) {
        IntermediateRollCatalogEntity entity = require(specCode);
        entity.active = false;
    }

    public IntermediateRollCatalogEntity requireEntity(String specCode) {
        return require(specCode);
    }

    private static IntermediateRollCatalogEntity require(String specCode) {
        IntermediateRollCatalogEntity entity = IntermediateRollCatalogEntity.findBySpecCode(specCode);
        if (entity == null) {
            throw new NotFoundException("intermediate spec not found: " + specCode);
        }
        return entity;
    }

    private static void validate(IntermediateRollCatalogDto dto) {
        if (dto.specCode() == null || dto.specCode().isBlank()) {
            throw new BadRequestException("specCode required");
        }
        if (dto.widthMm() == null || dto.widthMm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("widthMm must be positive");
        }
        if (dto.lengthMm() == null || dto.lengthMm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("lengthMm must be positive");
        }
        if (dto.cuttingMethod() == null || dto.cuttingMethod().isBlank()) {
            throw new BadRequestException("cuttingMethod required");
        }
    }

    private static void apply(IntermediateRollCatalogEntity entity, IntermediateRollCatalogDto dto) {
        entity.specCode = dto.specCode();
        entity.widthMm = dto.widthMm();
        entity.lengthMm = dto.lengthMm();
        entity.cuttingMethod = dto.cuttingMethod();
        entity.kerfMm = dto.kerfMm() != null ? dto.kerfMm() : BigDecimal.ZERO;
        entity.active = dto.active();
    }

    static IntermediateRollCatalogDto toDto(IntermediateRollCatalogEntity entity) {
        return new IntermediateRollCatalogDto(
                entity.specCode,
                entity.widthMm,
                entity.lengthMm,
                entity.cuttingMethod,
                entity.kerfMm,
                entity.active);
    }
}
