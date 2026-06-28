package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "child_slitting_order", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "order_code"}))
public class ChildSlittingOrderEntity extends WorkspaceScopedEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Column(name = "order_code", nullable = false, length = 128)
    public String orderCode;

    @Column(name = "width_mm", nullable = false)
    public BigDecimal widthMm;

    @Column(name = "length_mm", nullable = false)
    public BigDecimal lengthMm;

    @Column(name = "thickness_mm")
    public BigDecimal thicknessMm;

    @Column(name = "quantity", nullable = false)
    public int quantity = 1;

    @Column(name = "priority", nullable = false)
    public int priority;

    @Column(name = "sales_order_no", length = 128)
    public String salesOrderNo;

    @Column(name = "sales_order_line_no")
    public Integer salesOrderLineNo;

    @Column(name = "work_order_no", length = 128)
    public String workOrderNo;

    /** 本分切需求对应的半成品/规格料号 */
    @Column(name = "product_code", length = 256)
    public String productCode;

    /** 所属需求 BOM 根（通常关联销售订单成品/半成品） */
    @Column(name = "finished_product_code", length = 256)
    public String finishedProductCode;

    @Column(name = "status", nullable = false, length = 32)
    public String status = STATUS_OPEN;

    @Column(name = "created_ts", nullable = false)
    public LocalDateTime createdTs = LocalDateTime.now();

    public static ChildSlittingOrderEntity findByOrderCode(String orderCode) {
        return find("workspaceId = ?1 and orderCode = ?2", ws(), orderCode).firstResult();
    }

    public static List<ChildSlittingOrderEntity> listInWorkspace() {
        return list("workspaceId = ?1 order by priority desc, orderCode", ws());
    }
}
