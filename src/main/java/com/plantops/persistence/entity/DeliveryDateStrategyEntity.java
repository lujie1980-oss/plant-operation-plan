package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "delivery_date_strategy", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "customer_code", "product_code"
}))
public class DeliveryDateStrategyEntity extends WorkspaceScopedEntity {

    public String customerCode;

    public String productCode;

    @Column(name = "delivery_granularity")
    public String deliveryGranularity = "DAILY";

    public int earlyAllowDays;

    public int lateAllowDays;

    public BigDecimal earlyPenaltyCoef = BigDecimal.ONE;

    public BigDecimal latePenaltyCoef = BigDecimal.ONE;

    public static List<DeliveryDateStrategyEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static DeliveryDateStrategyEntity findByKey(String customerCode, String productCode) {
        return find(
                        "workspaceId = ?1 and customerCode = ?2 and productCode = ?3",
                        ws(),
                        customerCode,
                        productCode)
                .firstResult();
    }
}
