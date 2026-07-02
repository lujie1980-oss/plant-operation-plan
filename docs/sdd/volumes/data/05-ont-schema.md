# 数据卷 · Ontology 列级 Schema（`ont_*` · TODO-12 P0）

> **状态：** **V65 已落地**（`db/migration-postgresql/V65__ont_p0.sql` · PostgreSQL）。H2 legacy 仍用 `db/migration/`。  
> **属性目录：** [§5.20](../../core/05-domain-model.md#520-实体属性目录todo-21-phase-2) · [appendix](../../core/05-domain-model-appendix-fields.md)

---

## 公共列约定（P0 实体表）

| 列 | SQL 类型 | 可空 | 说明 |
|----|----------|------|------|
| `workspace_id` | VARCHAR(64) | N | ENT-WS |
| `revision_id` | VARCHAR(128) | N | FK → `ont_revision` |
| `entity_id` | VARCHAR(128) | N | = `OntologyIds` 前缀 ID |
| `created_at` | TIMESTAMPTZ | N | |
| `updated_at` | TIMESTAMPTZ | N | |
| PK | | | `(workspace_id, revision_id, entity_id)` |

---

## P0 表（V65）

### 容器

| 表 | PK | 主要列 |
|----|-----|--------|
| `ont_revision` | `(workspace_id, revision_id)` | `status` DRAFT/COMMITTED/ABANDONED/ARCHIVED · `parent_revision_id` · `plan_version_id` · `session_id` · `persistence_mode` FULL/PARTIAL · `change_seq` · `committed_at` |
| `ont_revision_head` | `(workspace_id, scope_key)` | `revision_id` → HEAD 指针（`WORKSPACE` / `SESSION:*` / `PLAN:*`） |
| `ont_change_log` | `(workspace_id, revision_id, change_seq)` | `change_type` · `entity_type` · `entity_id` · `payload_json` JSONB |
| `ont_session` | `(workspace_id, session_id)` | `draft_revision_id` · `base_revision_id` · `delivery_id` · `trial_revision` · `solve_profile_json` · `optimizer_result_json` · `expires_at` |

### 核心业务（revision 内快照）

| 表 | ENT | 业务列（除公共列外） |
|----|-----|---------------------|
| `ont_demand` | ENT-DEM | `product_code`, `pisp_id`, `quantity`, `need_date`, `priority`, `source_type`, `source_id` |
| `ont_supply_order` | ENT-SO | `product_code`, `pisp_id`, `quantity`, `need_date`, `status`, `type` |
| `ont_operation` | ENT-OP | `supply_order_id`, `plan_unit_id`, `sequence_nr`, `routing_sequence_no`, `operation_name`, 时长/窗口/planned 时间戳, `infeasible` — 对齐 `Operation.java` |
| `ont_fulfillment` | ENT-FF | `demand_id`, `supply_id`, `quantity`, `type` |
| `ont_pispp` | ENT-PISPP | `pisp_id`, `period_id`, 库存/计划/缺口各 `double` 字段 — 对齐 `ProductInStockingPointPeriod.java` |
| `ont_period` | ENT-PER | `sequence_nr`, `start_date`, `end_date`, `granularity`, `shift_id`, `parent_period_id`, `start_date_time`, `end_date_time`, `is_leaf` — 对齐 `Period.java`（**V67 · TODO-21**） |
| `ont_srp` | ENT-SRP | `standard_resource_id`, `period_id`, `total_capacity`, `*_downtime`, `reserved/available/free/overload_capacity` |
| `ont_resource_capacity_assignment` | ENT-RCA | `operation_id`, `operation_on_standard_resource_id`, `standard_resource_period_id`, `assigned_minutes`, `operation_total_minutes`, `locked`, `parallel_group_id` |

## P1/P2 扩展（未含 V65/V67）

| 表 | 说明 |
|----|------|
| ~~`ont_period`~~ | **已落地 V67**（shift 列 · ADR-16） |
| `ont_physical_resource_period` | ENT-PRP · TODO-24 P4 |
| Master 快照 | `ont_product`, `ont_routing*`, … |
| `ont_bom_dependency` | COMMITTED 真相 · 随 P1 Restorer |
| `ont_scheduling_slot` | **ADR-16 不写入** |

---

**Flyway：** `classpath:db/migration-postgresql` · 验收：`OntP0SchemaMigrationTest`（需 PG :5432）

**回指：** [05-domain-model.md §5.14](../../core/05-domain-model.md) · [ont-postgres-dev.md](../../../ont-postgres-dev.md)
