# §5.20 附录 — 实体属性目录（P0 核心）

> **状态：** TODO-21 Phase 2 进行中。列级 SQL 见 Phase 3 [`05-ont-schema.md`](../volumes/data/05-ont-schema.md)。  
> **日历链实体**（ENT-PER · ENT-PRP · ENT-SRP · MOD-CAL）已写入 [05-domain-model.md §5.20.0~5.20.7](./05-domain-model.md#520-实体属性目录todo-21-phase-2)。

## 目录模板

| 列 | 含义 |
|----|------|
| `属性` | Java 字段 / DB 列 |
| `类型` | Java 类型 · SQL 类型（Phase 3） |
| `必填` | Y/N |
| `来源` | `memory` · `md_*` · `txn_*` · `derived` · `solver` · `rol` |
| `RULE/SCN` | 规范锚点 |
| `现状` | `implemented` · `spec-only` · `legacy-only` |

---

## ENT-OP（Operation）

**Java：** `com.plantops.ontology.supply.Operation` · **表（目标）：** `ont_operation`

| 属性 | 类型 | 必填 | 来源 | RULE/SCN | 现状 |
|------|------|------|------|----------|------|
| `id` | String | Y | derived | — | implemented |
| `supplyOrderId` | String | Y | memory | — | implemented |
| `planUnitId` | String | Y | memory | — | implemented |
| `sequenceNr` | int | Y | derived | OP 序 | implemented |
| `routingSequenceNo` | int | Y | md_* | RULE-MP-06 | implemented |
| `operationName` | String | Y | md_* | — | implemented |
| `productionDuration` | long | Y | md_* / derived | RULE-SUP-02 · 秒 | implemented |
| `preprocessingTime` | long | Y | md_* | setup · 秒 | implemented |
| `postprocessingTime` | long | Y | md_* / derived | RULE-SUP-02 | implemented |
| `segmentIndex` | int | Y | derived | 拆段 | implemented |
| `lastSegment` | boolean | Y | derived | — | implemented |
| `parallelGroupId` | String | N | md_* / CFG | RULE-MP-08 | implemented |
| `locked` | boolean | N | rol / CFG | Firm | implemented |
| `earliestPossibleStartOwn` | datetime | N | derived | JIT/CTP | implemented |
| `earliestPossibleEndOwn` | datetime | N | derived | — | implemented |
| `earliestPossibleStartTotal` | datetime | N | derived | 串行制约 | implemented |
| `earliestPossibleEndTotal` | datetime | N | derived | — | implemented |
| `latestDesiredStart` | datetime | N | derived | JIT | implemented |
| `latestDesiredEnd` | datetime | N | derived | — | implemented |
| `plannedStartTotal` | datetime | N | solver | optimize | implemented |
| `plannedEndTotal` | datetime | N | solver | optimize | implemented |
| `infeasible` | boolean | Y | derived | VAL | implemented |

---

## ENT-COLD（CustomerOrderLineDelivery）

**Java：** `com.plantops.ontology.demand.CustomerOrderLineDelivery`

| 属性 | 类型 | 必填 | 来源 | RULE/SCN | 现状 |
|------|------|------|------|----------|------|
| `id` | String | Y | txn_* | ADR-06 | implemented |
| `customerOrderLineId` | String | Y | txn_* | — | implemented |
| `deliveryQty` | double | Y | txn_* | RULE-DEM-02 | implemented |
| `requestedDate` | date | Y | txn_* | SCN-07 | implemented |
| `latestDesiredDate` | date | N | txn_* / derived | RULE-DEM-03 | implemented |
| `status` | String | N | txn_* | — | implemented |
| `confirmedDeliveryDate` | date | N | txn_* / rol | TODO-10/11 | **spec-only** |
| `targetDeliveryQuantity` | double | N | txn_* | TODO-10/11 | **spec-only** |

---

## ENT-PISPP（ProductInStockingPointPeriod）

**Java：** `com.plantops.ontology.period.ProductInStockingPointPeriod` · **表（目标）：** `ont_pispp`

| 属性 | 类型 | 必填 | 来源 | RULE/SCN | 现状 |
|------|------|------|------|----------|------|
| `id` | String | Y | derived | — | implemented |
| `pispId` | String | Y | memory | — | implemented |
| `periodId` | String | Y | memory | 日 Period | implemented |
| `onHand` | double | Y | txn_* / derived | RULE-MRP-05 | implemented |
| `plannedSupplyTotal` | double | Y | derived / solver | — | implemented |
| `plannedSupplyTotalMrp` | double | Y | derived | MRP | implemented |
| `plannedSupplyTotalOptimized` | double | Y | solver | optimize | implemented |
| `plannedDemandQuantityTotal` | double | Y | derived | RULE-MRP-05 | implemented |
| `inventoryTargetQuantity` | double | Y | CFG | — | implemented |
| `plannedInventoryLevel` | double | Y | derived | RULE-MRP-05 | implemented |
| `replenishedInventoryLevel` | double | Y | derived | — | implemented |
| `stockShortageQuantity` | double | Y | derived | SCN-07a | implemented |

---

*其余 P0 实体见下文；列级 SQL 见 Phase 3。*

---

## ENT-DEM（Demand）

**Java：** `com.plantops.ontology.demand.Demand` · **表（目标）：** `ont_demand`

| 属性 | 类型 | 必填 | 来源 | RULE/SCN | 现状 |
|------|------|------|------|----------|------|
| `id` | String | Y | derived | — | implemented |
| `productCode` | String | Y | txn_* | — | implemented |
| `pispId` | String | Y | md_* | — | implemented |
| `quantity` | double | Y | txn_* | RULE-DEM-02 | implemented |
| `needDate` | date | Y | txn_* / derived | RULE-MRP-04 | implemented |
| `priority` | int | Y | CFG | RULE-DEM-01 | implemented |
| `sourceType` | enum | Y | derived | ENT-COLD/FC | implemented |
| `sourceId` | String | Y | txn_* | ADR-06 | implemented |

---

## ENT-SO（SupplyOrder）

**Java：** `com.plantops.ontology.supply.SupplyOrder` · **表（目标）：** `ont_supply_order`

| 属性 | 类型 | 必填 | 来源 | RULE/SCN | 现状 |
|------|------|------|------|----------|------|
| `id` | String | Y | txn_* | `= workOrderNo` | implemented |
| `productCode` | String | Y | txn_* | — | implemented |
| `pispId` | String | Y | md_* | — | implemented |
| `quantity` | double | Y | txn_* | RULE-SUP-01 | implemented |
| `needDate` | date | Y | txn_* / derived | — | implemented |
| `status` | enum | Y | txn_* | Firm WO | implemented |
| `type` | enum | Y | derived | MRP/MANUAL | implemented |

---

## ENT-FF（Fulfillment）

**Java：** `com.plantops.ontology.fulfillment.Fulfillment` · **表（目标）：** `ont_fulfillment`

| 属性 | 类型 | 必填 | 来源 | RULE/SCN | 现状 |
|------|------|------|------|----------|------|
| `id` | String | Y | derived | — | implemented |
| `demandId` | String | Y | memory | RULE-FF-* | implemented |
| `supplyId` | String | Y | memory | — | implemented |
| `quantity` | double | Y | derived / rol | RULE-FF-08 | implemented |
| `type` | enum | Y | derived | PEG-INV/WO/SH | implemented |

---

## ENT-RS（RoutingStep）

**Java：** `com.plantops.ontology.master.RoutingStep` · **表（目标）：** `ont_routing_step`

| 属性 | 类型 | 必填 | 来源 | RULE/SCN | 现状 |
|------|------|------|------|----------|------|
| `id` | String | Y | md_* | `RS-{pisp}-{seq}` | implemented |
| `routingId` | String | Y | md_* | ENT-RT | implemented |
| `sequenceNo` | int | Y | md_* | RULE-MP-06 | implemented |
| `operationName` | String | Y | md_* | — | implemented |
| `yieldRate` | double | N | md_* | RULE-SUP-04 | **spec-only**（Java 类未含；见 md_routing_step） |
