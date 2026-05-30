package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;



import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;



@Entity

@Table(name = "bom_component")

public class BomComponentEntity extends WorkspaceScopedEntity {



    /** 成品料号：该套 BOM 对应的最终成品 */
    public String finishedProductCode;

    public String bomId;

    public String bomVersion;

    /** 产品代码：BOM 父项 */
    public String parentProductCode;

    /** 组件代码：BOM 子项 */
    public String componentProductCode;

    public BigDecimal componentQty;

    public boolean isCriticalComponent;

    public LocalDate bomEffectiveFrom;

    public LocalDate bomEffectiveTo;

    public LocalDate componentEffectiveFrom;

    public LocalDate componentEffectiveTo;

    /** 组件损耗率（小数，如 0.05 表示 5%） */
    public BigDecimal scrapRate;

    /** 生产父项 {@link #parentProductCode} 时的最小/固定批量 */
    public BigDecimal lotSize;

    /** 生产父项时的批量倍数 */
    public BigDecimal lotSizeMultiple;



    public static List<BomComponentEntity> listInWorkspace() {

        return list("workspaceId", ws());

    }



    public static List<BomComponentEntity> findByParent(String parentProductCode) {

        return list("workspaceId = ?1 and parentProductCode = ?2", ws(), parentProductCode);

    }

    public static List<BomComponentEntity> findByFinishedProduct(String finishedProductCode) {
        return list("workspaceId = ?1 and finishedProductCode = ?2", ws(), finishedProductCode);
    }

    public static List<BomComponentEntity> findByFinishedAndParent(String finishedProductCode, String parentProductCode) {
        return list(
                "workspaceId = ?1 and finishedProductCode = ?2 and parentProductCode = ?3",
                ws(),
                finishedProductCode,
                parentProductCode);
    }

    /** 按成品料号范围展开；无成品料号时回退为仅按父项查询。 */
    public static List<BomComponentEntity> findChildren(String finishedProductCode, String parentProductCode) {
        if (finishedProductCode != null && !finishedProductCode.isBlank()) {
            List<BomComponentEntity> scoped = findByFinishedAndParent(finishedProductCode, parentProductCode);
            if (!scoped.isEmpty()) {
                return scoped;
            }
        }
        return findByParent(parentProductCode);
    }

    /** 工单对应销售订单行的成品料号；无订单时用工单产品代码。 */
    public static String resolveFinishedProduct(WorkOrderEntity wo) {
        if (wo == null) {
            return null;
        }
        if (wo.salesOrderNo != null) {
            SalesOrderLineEntity order = SalesOrderLineEntity.find(
                            "workspaceId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3",
                            ws(),
                            wo.salesOrderNo,
                            wo.salesOrderLineNo)
                    .firstResult();
            if (order != null && order.productCode != null && !order.productCode.isBlank()) {
                return order.productCode;
            }
        }
        return wo.productCode;
    }

}


