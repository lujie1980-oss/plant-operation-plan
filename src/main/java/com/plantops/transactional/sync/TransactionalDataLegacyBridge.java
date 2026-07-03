package com.plantops.transactional.sync;

import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.TxnCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.TxnCustomerOrderLineEntity;
import com.plantops.persistence.entity.TxnInventoryBalanceEntity;
import com.plantops.persistence.entity.TxnOperationOsrEntity;
import com.plantops.persistence.entity.TxnSupplyOrderEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

/**
 * §12 legacy 过渡：txn_* sync 后 upsert legacy 表（已退役 · TODO-14 T5）。
 *
 * @deprecated T5 后 OG 装载仅使用 txn_*；保留类供历史对照与手工迁移。
 */
@Deprecated
@ApplicationScoped
public class TransactionalDataLegacyBridge {

    @Transactional
    public void syncFromTxn() {
        syncSalesOrderLines();
        syncFirmWorkOrders();
        syncInventory();
    }

    private void syncSalesOrderLines() {
        for (TxnCustomerOrderLineDeliveryEntity cold : TxnCustomerOrderLineDeliveryEntity.listInWorkspace()) {
            TxnCustomerOrderLineEntity col = TxnCustomerOrderLineEntity.find(
                            "workspaceId = ?1 and customerOrderNo = ?2 and lineNo = ?3",
                            TxnCustomerOrderLineEntity.ws(),
                            cold.customerOrderNo,
                            cold.lineNo)
                    .firstResult();
            if (col == null) {
                continue;
            }
            SalesOrderLineEntity existing =
                    SalesOrderLineEntity.findByKey(cold.customerOrderNo, cold.lineNo);
            if (existing != null) {
                existing.orderQty = cold.deliveryQty;
                existing.dueDate = cold.requestedDate != null ? cold.requestedDate : existing.dueDate;
                if (cold.status != null) {
                    existing.status = cold.status;
                }
                continue;
            }
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = cold.customerOrderNo;
            line.salesOrderLineNo = cold.lineNo;
            line.productCode = col.productCode;
            line.orderQty = cold.deliveryQty != null ? cold.deliveryQty : col.orderQty;
            line.dueDate = cold.requestedDate != null ? cold.requestedDate : java.time.LocalDate.now();
            line.status = cold.status != null ? cold.status : "OPEN";
            line.stampWorkspace();
            line.persist();
        }
    }

    private void syncFirmWorkOrders() {
        for (TxnSupplyOrderEntity so : TxnSupplyOrderEntity.listInWorkspace()) {
            if (!TxnSupplyOrderEntity.FIRM_STATUS_FIRM.equals(so.firmStatus)) {
                continue;
            }
            String resourceId = TxnOperationOsrEntity.listInWorkspace().stream()
                    .filter(osr -> so.supplyOrderId.equals(osr.supplyOrderId))
                    .sorted(java.util.Comparator.comparingInt(osr -> osr.resourcePriority))
                    .map(osr -> osr.standardResourceCode)
                    .findFirst()
                    .orElse("UNASSIGNED");
            WorkOrderEntity existing = WorkOrderEntity.findByNo(so.supplyOrderId);
            if (existing != null) {
                existing.productCode = so.productCode;
                existing.quantity = so.quantity;
                existing.needDate = so.needDate;
                existing.dispatchStatus = so.dispatchStatus;
                existing.resourceId = resourceId;
                existing.sourceType = WorkOrderEntity.SOURCE_MANUAL;
                continue;
            }
            WorkOrderEntity wo = new WorkOrderEntity();
            wo.workOrderNo = so.supplyOrderId;
            wo.productCode = so.productCode;
            wo.quantity = so.quantity != null ? so.quantity : BigDecimal.ZERO;
            wo.needDate = so.needDate;
            wo.parentWorkOrderNo = so.parentSupplyOrderId;
            wo.dispatchStatus = so.dispatchStatus;
            wo.resourceId = resourceId;
            wo.sequenceNo = 1;
            wo.sourceType = WorkOrderEntity.SOURCE_MANUAL;
            wo.stampWorkspace();
            wo.persist();
        }
    }

    private void syncInventory() {
        for (TxnInventoryBalanceEntity bal : TxnInventoryBalanceEntity.listInWorkspace()) {
            InventoryEntity existing = InventoryEntity.find(
                            "workspaceId = ?1 and productCode = ?2 and stockingPointCode = ?3",
                            InventoryEntity.ws(),
                            bal.productCode,
                            bal.stockingPointCode)
                    .firstResult();
            if (existing != null) {
                existing.onhandQty = bal.onHandQty;
                continue;
            }
            InventoryEntity inv = new InventoryEntity();
            inv.productCode = bal.productCode;
            inv.stockingPointCode = bal.stockingPointCode;
            inv.onhandQty = bal.onHandQty != null ? bal.onHandQty : BigDecimal.ZERO;
            inv.stampWorkspace();
            inv.persist();
        }
    }
}
