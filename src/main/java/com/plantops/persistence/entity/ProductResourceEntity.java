package com.plantops.persistence.entity;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;



@Entity

@Table(name = "product_resource", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "product_code", "resource_id"

}))

public class ProductResourceEntity extends WorkspaceScopedEntity {

    public static final int DEFAULT_RESOURCE_PRIORITY = 1;

    public String productCode;

    public String resourceId;

    public int setupTimeMinutes;



    @Column(name = "sequence_no")

    public Integer sequenceNo;

    /** 同工序多资源时的占用优先级：数值越小越优先（1 最高）。 */
    @Column(name = "resource_priority")
    public Integer resourcePriority = DEFAULT_RESOURCE_PRIORITY;

    @Column(name = "operation_name")

    public String operationName;



    @Column(name = "process_time_seconds")

    public BigDecimal processTimeSeconds;

    /** A/B 料：BOM 阶层（二阶 / 一阶 / 成品） */
    @Column(name = "bom_level")
    public String bomLevel;

    /** 线材：相同线材宜集中生产 */
    @Column(name = "wire_material")
    public String wireMaterial;

    /** 关键物料：相同关键物料宜集中生产 */
    @Column(name = "key_material")
    public String keyMaterial;

    @Column(name = "male_female_end")
    public String maleFemaleEnd;

    @Column(name = "total_branch")
    public String totalBranch;

    /** 制造人力：工序标准人力需求 */
    @Column(name = "standard_labor")
    public BigDecimal standardLabor;

    /** workspace 级 Custom 字段（JSON）；legacy 列在过渡期双写 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extensions")
    public Map<String, Object> extensions;

    public static List<ProductResourceEntity> listInWorkspace() {

        return list("workspaceId", ws());

    }

    public static boolean hasRouting(String productCode) {
        return count("workspaceId = ?1 and productCode = ?2", ws(), productCode) > 0;
    }

    public static ProductResourceEntity findFirstByProduct(String productCode) {
        return find("workspaceId = ?1 and productCode = ?2", ws(), productCode).firstResult();
    }

    public static List<ProductResourceEntity> findByProductOrdered(String productCode) {
        return list("workspaceId = ?1 and productCode = ?2 order by sequenceNo, resourcePriority, id", ws(), productCode);
    }

    public static ProductResourceEntity findByProductAndResource(String productCode, String resourceId) {
        return find("workspaceId = ?1 and productCode = ?2 and resourceId = ?3", ws(), productCode, resourceId)
                .firstResult();
    }

    public static ProductResourceEntity findByProductAndOperation(String productCode, String operationName) {
        if (operationName == null || operationName.isBlank()) {
            return findFirstByProduct(productCode);
        }
        return find("workspaceId = ?1 and productCode = ?2 and operationName = ?3",
                ws(), productCode, operationName).firstResult();
    }

}


