package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.ChildSlittingOrderDto;
import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class ChildSlittingOrderService {

    public List<ChildSlittingOrderDto> list() {
        return ChildSlittingOrderEntity.listInWorkspace().stream().map(ChildSlittingOrderService::toDto).toList();
    }

    @Transactional
    public ChildSlittingOrderDto create(ChildSlittingOrderDto dto) {
        validate(dto);
        if (ChildSlittingOrderEntity.findByOrderCode(dto.orderCode()) != null) {
            throw new BadRequestException("orderCode already exists: " + dto.orderCode());
        }
        ChildSlittingOrderEntity entity = new ChildSlittingOrderEntity();
        entity.stampWorkspace();
        apply(entity, dto);
        entity.persist();
        return toDto(entity);
    }

    @Transactional
    public ChildSlittingOrderDto update(String orderCode, ChildSlittingOrderDto dto) {
        validate(dto);
        ChildSlittingOrderEntity entity = require(orderCode);
        apply(entity, dto);
        return toDto(entity);
    }

    @Transactional
    public void archive(String orderCode) {
        ChildSlittingOrderEntity entity = require(orderCode);
        entity.status = ChildSlittingOrderEntity.STATUS_ARCHIVED;
    }

    public ChildSlittingOrderEntity requireEntity(String orderCode) {
        return require(orderCode);
    }

    private static ChildSlittingOrderEntity require(String orderCode) {
        ChildSlittingOrderEntity entity = ChildSlittingOrderEntity.findByOrderCode(orderCode);
        if (entity == null) {
            throw new NotFoundException("child order not found: " + orderCode);
        }
        return entity;
    }

    private static void validate(ChildSlittingOrderDto dto) {
        if (dto.orderCode() == null || dto.orderCode().isBlank()) {
            throw new BadRequestException("orderCode required");
        }
        if (dto.widthMm() == null || dto.widthMm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("widthMm must be positive");
        }
        if (dto.lengthMm() == null || dto.lengthMm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("lengthMm must be positive");
        }
        if (dto.quantity() <= 0) {
            throw new BadRequestException("quantity must be positive");
        }
    }

    private static void apply(ChildSlittingOrderEntity entity, ChildSlittingOrderDto dto) {
        entity.orderCode = dto.orderCode();
        entity.widthMm = dto.widthMm();
        entity.lengthMm = dto.lengthMm();
        entity.thicknessMm = dto.thicknessMm();
        entity.quantity = dto.quantity();
        entity.priority = dto.priority();
        entity.salesOrderNo = dto.salesOrderNo();
        entity.salesOrderLineNo = dto.salesOrderLineNo();
        entity.workOrderNo = dto.workOrderNo();
        entity.status = dto.status() != null ? dto.status() : ChildSlittingOrderEntity.STATUS_OPEN;
    }

    static ChildSlittingOrderDto toDto(ChildSlittingOrderEntity entity) {
        return new ChildSlittingOrderDto(
                entity.orderCode,
                entity.widthMm,
                entity.lengthMm,
                entity.thicknessMm,
                entity.quantity,
                entity.priority,
                entity.salesOrderNo,
                entity.salesOrderLineNo,
                entity.workOrderNo,
                entity.status);
    }
}
