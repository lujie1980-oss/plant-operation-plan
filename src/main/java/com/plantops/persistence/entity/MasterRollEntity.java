package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "master_roll", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "roll_code"}))
public class MasterRollEntity extends WorkspaceScopedEntity {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_CONSUMED = "CONSUMED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Column(name = "roll_code", nullable = false, length = 128)
    public String rollCode;

    @Column(name = "width_mm", nullable = false)
    public BigDecimal widthMm;

    @Column(name = "length_mm", nullable = false)
    public BigDecimal lengthMm;

    @Column(name = "thickness_mm")
    public BigDecimal thicknessMm;

    @Column(name = "material_code", length = 64)
    public String materialCode;

    @Column(name = "kerf_longitudinal_mm", nullable = false)
    public BigDecimal kerfLongitudinalMm = BigDecimal.ZERO;

    @Column(name = "kerf_transverse_mm", nullable = false)
    public BigDecimal kerfTransverseMm = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 32)
    public String status = STATUS_AVAILABLE;

    @Column(name = "created_ts", nullable = false)
    public LocalDateTime createdTs = LocalDateTime.now();

    public static MasterRollEntity findByRollCode(String rollCode) {
        return find("workspaceId = ?1 and rollCode = ?2", ws(), rollCode).firstResult();
    }

    public static List<MasterRollEntity> listInWorkspace() {
        return list("workspaceId = ?1 order by rollCode", ws());
    }
}
