package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "forecast_demand", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "forecast_id"
}))
public class ForecastDemandEntity extends WorkspaceScopedEntity {

    @Column(name = "forecast_id", nullable = false, length = 128)
    public String forecastId;

    @Column(name = "product_code", nullable = false, length = 64)
    public String productCode;

    @Column(nullable = false)
    public BigDecimal quantity;

    @Column(name = "forecast_period", length = 32)
    public String forecastPeriod;

    @Column(name = "need_date", nullable = false)
    public LocalDate needDate;

    public BigDecimal confidence = new BigDecimal("0.8");

    public static List<ForecastDemandEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static ForecastDemandEntity findByForecastId(String forecastId) {
        return find("workspaceId = ?1 and forecastId = ?2", ws(), forecastId).firstResult();
    }
}
