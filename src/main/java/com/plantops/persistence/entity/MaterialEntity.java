package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "material")
public class MaterialEntity extends WorkspaceScopedEntity {

    @Column(name = "material_code", nullable = false, length = 128)
    public String materialCode;

    @Column(name = "material_name", length = 256)
    public String materialName;

    @Column(name = "uom_code", length = 64)
    public String uomCode;

    @Column(name = "material_type", length = 64)
    public String materialType;

    @Column(name = "site_code", length = 64)
    public String siteCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extensions")
    public Map<String, Object> extensions;

    public static List<MaterialEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static MaterialEntity findByCode(String materialCode) {
        return find("workspaceId = ?1 and materialCode = ?2", ws(), materialCode).firstResult();
    }
}

