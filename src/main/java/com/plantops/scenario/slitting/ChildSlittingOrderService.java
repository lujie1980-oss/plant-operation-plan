package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.ChildSlittingOrderDto;
import com.plantops.api.dto.slitting.ImportChildOrdersFromDemandRequest;
import com.plantops.api.dto.slitting.ImportChildOrdersFromDemandResult;
import com.plantops.config.ParameterRegistry;
import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class ChildSlittingOrderService {

    @Inject
    ParameterRegistry parameters;

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

    @Transactional
    public ImportChildOrdersFromDemandResult importFromDemand(ImportChildOrdersFromDemandRequest request) {
        BigDecimal defaultWidth = request != null && request.defaultWidthMm() != null
                ? request.defaultWidthMm()
                : BigDecimal.valueOf(parameters.getInt("slitting_default_child_width_mm", 200));
        BigDecimal defaultLength = request != null && request.defaultLengthMm() != null
                ? request.defaultLengthMm()
                : BigDecimal.valueOf(parameters.getInt("slitting_default_child_length_mm", 1000));
        boolean skipExisting = request == null || request.skipExisting();

        List<SalesOrderLineEntity> lines = SalesOrderLineEntity.listInWorkspace().stream()
                .filter(o -> !"CANCELLED".equals(o.status))
                .filter(o -> request == null
                        || request.salesOrderNos() == null
                        || request.salesOrderNos().isEmpty()
                        || request.salesOrderNos().contains(o.salesOrderNo))
                .toList();

        int created = 0;
        int skipped = 0;
        List<ChildSlittingOrderDto> createdOrders = new java.util.ArrayList<>();
        for (SalesOrderLineEntity line : lines) {
            String orderCode = "CO-" + line.salesOrderNo + "-" + line.salesOrderLineNo;
            ChildSlittingOrderEntity existing = findBySalesOrderLine(line.salesOrderNo, line.salesOrderLineNo);
            if (existing != null) {
                if (skipExisting) {
                    skipped++;
                    continue;
                }
                existing.quantity = line.orderQty != null ? line.orderQty.intValue() : 1;
                existing.priority = line.priority;
                existing.widthMm = defaultWidth;
                existing.lengthMm = defaultLength;
                createdOrders.add(toDto(existing));
                skipped++;
                continue;
            }
            if (ChildSlittingOrderEntity.findByOrderCode(orderCode) != null) {
                skipped++;
                continue;
            }
            ChildSlittingOrderEntity entity = new ChildSlittingOrderEntity();
            entity.stampWorkspace();
            entity.orderCode = orderCode;
            entity.widthMm = defaultWidth;
            entity.lengthMm = defaultLength;
            entity.quantity = line.orderQty != null ? Math.max(1, line.orderQty.intValue()) : 1;
            entity.priority = line.priority;
            entity.salesOrderNo = line.salesOrderNo;
            entity.salesOrderLineNo = line.salesOrderLineNo;
            entity.status = ChildSlittingOrderEntity.STATUS_OPEN;
            entity.persist();
            created++;
            createdOrders.add(toDto(entity));
        }
        return new ImportChildOrdersFromDemandResult(created, skipped, createdOrders);
    }

    private static ChildSlittingOrderEntity findBySalesOrderLine(String salesOrderNo, int lineNo) {
        return ChildSlittingOrderEntity.find(
                "workspaceId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3",
                ChildSlittingOrderEntity.ws(),
                salesOrderNo,
                lineNo).firstResult();
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
        entity.productCode = dto.productCode();
        entity.finishedProductCode = dto.finishedProductCode();
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
                entity.productCode,
                entity.finishedProductCode,
                entity.status);
    }
}
