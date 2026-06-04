package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "intermediate_roll_catalog", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "spec_code"}))
public class IntermediateRollCatalogEntity extends WorkspaceScopedEntity {

    @Column(name = "spec_code", nullable = false, length = 128)
    public String specCode;

    @Column(name = "width_mm", nullable = false)
    public BigDecimal widthMm;

    @Column(name = "length_mm", nullable = false)
    public BigDecimal lengthMm;

    @Column(name = "cutting_method", nullable = false, length = 32)
    public String cuttingMethod;

    @Column(name = "kerf_mm", nullable = false)
    public BigDecimal kerfMm = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    public boolean active = true;

    public static IntermediateRollCatalogEntity findBySpecCode(String specCode) {
        return find("workspaceId = ?1 and specCode = ?2", ws(), specCode).firstResult();
    }

    public static List<IntermediateRollCatalogEntity> listInWorkspace() {
        return list("workspaceId = ?1 order by specCode", ws());
    }

    public static List<IntermediateRollCatalogEntity> listActiveInWorkspace() {
        return list("workspaceId = ?1 and active = true order by specCode", ws());
    }
}
