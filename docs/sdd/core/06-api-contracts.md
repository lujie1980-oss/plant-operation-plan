# §6 接口与集成契约

> **约定：** Base `/api/v1`；Header `X-Workspace-Id` 必填（业务 API）。  
> **OpenAPI 骨架（TODO-02）：** 由本节自动生成 [`docs/api/openapi.yaml`](../../api/openapi.yaml)（`GenerateOpenApiFromSdd` · `OpenApiSpecCoverageTest`）。
> **IAM（ADR-13）：** 生产环境须 **认证**；业务 API 另校验 WS 成员与模块权限（§18 · RULE-IAM-*）。**v1 已落地** Filter + JWT/OIDC。
> 响应 JSON；错误时 HTTP 4xx/5xx + 消息体。

---

## API-FC-01 交付满足链

| 项 | 值 |
|----|-----|
| **ID** | API-FC-01 |
| **场景** | SCN-01c |
| **方法** | `GET` |
| **路径** | `/api/v1/ontology/fulfillment/deliveries/{deliveryId}/fulfillment-chain` |
| **Query** | `masterPlanVersionId`（可选） |
| **响应** | `OrderFulfillmentChainDto` |
| **错误** | 404：COLD 不存在 |

**备选路径（遗留）：** `GET /api/v1/demand/demand-pool/{so}/{line}/fulfillment-chain`

---

## API-FC-02 交付列表

| 项 | 值 |
|----|-----|
| **ID** | API-FC-02 |
| **方法** | `GET` |
| **路径** | `/api/v1/ontology/fulfillment/deliveries` |
| **Query** | `masterPlanVersionId`（可选） |
| **响应** | `CustomerOrderLineDelivery` 摘要列表 |

---

## API-DEM-01 订单行 / 交付批次动作

| 项 | 值 |
|----|-----|
| **ID** | API-DEM-01 |
| **场景** | SCN-01d~h |
| **方法** | `POST` |
| **路径** | `/api/v1/demand/demand-pool/{salesOrderNo}/{salesOrderLineNo}/actions/{action}` |
| **备选路径** | `/api/v1/ontology/fulfillment/deliveries/{deliveryId}/actions/{action}` |
| **Body** | `OrderDemandActionRequest`（masterPlanVersionId、promiseDateOverride、useFeedbackOverlay 等） |
| **响应** | `OrderDemandActionResult`（message、fulfillmentChain、promiseDate） |

**action 枚举：**

| action | 场景 | 说明 |
|--------|------|------|
| `INFINITE_PLAN_JIT` | SCN-01g | 无限能力 JIT 建链（别名 `BUILD_UPSTREAM_CHAIN`） |
| `FINITE_PLAN` | SCN-01h, SCN-01b | 单交付有限能力 optimize（别名 `PLAN_FINITE`） |
| `CONFIRM_PROMISE_DATE` | SCN-01d | 确认承诺交期 |
| `CANCEL_PLAN` | SCN-01e | 取消订单计划 |
| `CANCEL_PROMISE` | SCN-01f | 取消订单承诺（**已实现 2026-07-02** · 仅清 `promiseDate`） |

**预览：** `POST .../actions/CONFIRM_PROMISE_DATE/preview` → `PromiseDatePreviewDto`（SCN-01d 默认交期）

---

> **领域模型：** ENT-SES / ENT-SBX / ENT-PV · 生命周期见 **[§5.19](./05-domain-model.md#519-平台与-sessionent-ws--ent-ses--ent-sbx--ent-pv)**。

## API-SES-01 创建 Session

| 项 | 值 |
|----|-----|
| **ID** | API-SES-01 |
| **场景** | SCN-01b, SCN-01h, SCN-T02 |
| **方法** | `POST` |
| **路径** | `/api/v1/master-plan/sessions` |
| **Body** | `CreateMasterPlanSessionRequest`（strategyId、basePlanVersionId 等） |
| **响应** | `201` + `MasterPlanSessionDto` |

---

## API-SES-02 推演 simulate

| 项 | 值 |
|----|-----|
| **ID** | API-SES-02 |
| **场景** | SCN-01a, SCN-03c, SCN-04c（ATP/试算 simulate） |
| **规则** | RULE-SES-01 |
| **方法** | `POST` |
| **路径** | `/api/v1/master-plan/sessions/{sessionId}/simulate` |
| **Body** | `SimulateMasterPlanSessionRequest`（ChangeSet / needDate / SRP 等） |
| **响应** | `MasterPlanSessionSimulateResultDto` |

---

## API-SES-03 求解 optimize

| 项 | 值 |
|----|-----|
| **ID** | API-SES-03 |
| **场景** | SCN-01b CTP, SCN-01h, SCN-T01 |
| **方法** | `POST` |
| **路径** | `/api/v1/master-plan/sessions/{sessionId}/optimize` |
| **响应** | `MasterPlanSessionOptimizeResultDto`（含 hard/soft score） |

---

## API-SES-04 确认 confirm

| 项 | 值 |
|----|-----|
| **ID** | API-SES-04 |
| **场景** | SCN-T02 |
| **规则** | RULE-SES-02 |
| **方法** | `POST` |
| **路径** | `/api/v1/master-plan/sessions/{sessionId}/confirm` |
| **响应** | `MasterPlanSessionConfirmResultDto`（planVersionId） |

---

## API-SES-05 Session 快照

| 项 | 值 |
|----|-----|
| **ID** | API-SES-05 |
| **方法** | `GET` |
| **路径** | `/api/v1/master-plan/sessions/{sessionId}/supply-orders/{supplyOrderId}/operations` |
| **响应** | `List<OperationSnapshotDto>` |

---

## API-MP-01 制定主计划（全流水线）

| 项 | 值 |
|----|-----|
| **ID** | API-MP-01 |
| **场景** | SCN-06（PlanningRun） |
| **方法** | `POST` |
| **路径** | `/api/v1/planning/run-full-pipeline` |
| **Body** | 策略、步骤开关 |
| **响应** | pipeline run id + planVersionId |

---

## API-MP-02 主计划求解

| 项 | 值 |
|----|-----|
| **ID** | API-MP-02 |
| **场景** | SCN-06（PlanningRun） |
| **方法** | `POST` |
| **路径** | `/api/v1/planning/master-plan/solve` |
| **响应** | `planVersionId` |

---

## API-RT-01 工艺主数据

| 项 | 值 |
|----|-----|
| **ID** | API-RT-01 |
| **场景** | SCN-T04 |
| **方法** | `GET` |
| **路径** | `/api/v1/ontology/master-model/products/{productCode}/routing` |
| **响应** | `RoutingDto` + `RoutingStepDetailDto[]`（含 RSOSR/RSIM/RSOM） |

---

## API-MAT-01 供需平衡 / PISPP 表

| 项 | 值 |
|----|-----|
| **ID** | API-MAT-01 |
| **场景** | SCN-07a, SCN-04b |
| **方法** | `GET` |
| **路径** | `/api/v1/ontology/material-planning/balance` |
| **Query** | `masterPlanVersionId`（可选） |
| **响应** | `MaterialRequirementReportDto`（DTO-MBP）；PISP × period 投影 |
| **说明** | 含 `periodHeaders` + `MaterialBalancePeriodDto` 期间桶列（2026-07-02）；日粒度 `days` 保留作过渡 |

**重算：** `POST .../material-planning/compute` — 触发 MRP 重算后返回同结构报告。

---

## API-MAT-02 供应路径候选与 EAT

| 项 | 值 |
|----|-----|
| **ID** | API-MAT-02 |
| **场景** | SCN-07c, SCN-07d |
| **方法** | `GET` |
| **路径** | `/api/v1/ontology/material-planning/pisps/{pispId}/routing-candidates` |
| **Query** | `periodFrom`, `periodTo`, `quantity`, `masterPlanVersionId` |
| **响应** | `SupplyRoutingCandidateDto[]`（routingId, pathPriority, steps[], earliestAchievableTime） |
| **说明** | **已实现**（2026-07-02）；EAT 基于工艺工时正推试算 |

---

## API-MAT-03 创建供应计划

| 项 | 值 |
|----|-----|
| **ID** | API-MAT-03 |
| **场景** | SCN-07b~d |
| **方法** | `POST` |
| **路径** | `/api/v1/ontology/material-planning/pisps/{pispId}/supply-plans` |
| **Body** | `CreateSupplyPlanRequest`（mode: `AUTO` \| `MANUAL` \| `OPTIMIZE`, periodFrom, periodTo, quantity, routingId?, needDate?） |
| **响应** | `CreateSupplyPlanResultDto`（supplyOrderIds[], routingId, earliestAchievableTime, updatedPisppSummary） |
| **规则** | RULE-MRP-01~03, RULE-FF-04 |
| **说明** | **已实现**（2026-07-02）；`AUTO` / `MANUAL` / `OPTIMIZE` |

| 项 | 值 |
|----|-----|
| **ID** | API-MAT-04 |
| **场景** | SCN-07e |
| **方法** | `GET` |
| **路径** | `/api/v1/ontology/material-planning/pisps/{pispId}/period-demands` |
| **Query** | `periodFrom`, `periodTo`（或 `periodId`）, `masterPlanVersionId` |
| **响应** | `PeriodDemandListDto`（DTO-PDL） |
| **说明** | **已实现**（2026-07-02） |

---

## API-MAT-05 Demand 可匹配 Supply

| 项 | 值 |
|----|-----|
| **ID** | API-MAT-05 |
| **场景** | SCN-07f |
| **方法** | `GET` |
| **路径** | `/api/v1/ontology/material-planning/demands/{demandId}/eligible-supplies` |
| **Query** | `masterPlanVersionId` |
| **响应** | `EligibleSupplyListDto`（DTO-PSM） |
| **规则** | RULE-FF-05 |
| **说明** | **已实现**（2026-07-02） |

---

## API-MAT-06 手工创建 Fulfillment（拖拽预留）

| 项 | 值 |
|----|-----|
| **ID** | API-MAT-06 |
| **场景** | SCN-07g |
| **方法** | `POST` |
| **路径** | `/api/v1/ontology/material-planning/fulfillments` |
| **Body** | `CreateFulfillmentRequest`（demandId, supplyId, quantity, source=`DRAG`） |
| **响应** | `FulfillmentDto` + 更新后 unpegged 摘要 |
| **规则** | RULE-FF-05, RULE-FF-07 |
| **说明** | **已实现**（2026-07-02）；写入 `ont_fulfillment` |

---

## API-MAT-07 自动预留

| 项 | 值 |
|----|-----|
| **ID** | API-MAT-07 |
| **场景** | SCN-07h, SCN-07i |
| **方法** | `POST` |
| **路径** | `/api/v1/ontology/material-planning/reservations/auto` |
| **Body** | `AutoReservationRequest`（anchorType: `DEMAND` \| `SUPPLY`, anchorId, maxQty?） |
| **响应** | `AutoReservationResultDto`（fulfillments[], reservedQty, remainingUnpeggedQty） |
| **规则** | RULE-FF-06 |
| **说明** | **已实现**（2026-07-02）；DEMAND/SUPPLY 锚点 |

---

## API-MAT-08 预留风险预警

| 项 | 值 |
|----|-----|
| **ID** | API-MAT-08 |
| **场景** | SCN-07j |
| **方法** | `GET` |
| **路径** | `/api/v1/ontology/material-planning/pisps/{pispId}/reservation-alerts` |
| **Query** | `periodFrom`, `periodTo`, `masterPlanVersionId` |
| **响应** | `ReservationAlertDto[]`（DTO-PRA） |
| **规则** | RULE-FF-07 |
| **说明** | **已实现**（2026-07-02） |

---

## API-IAM：用户与权限（§18 · ADR-13）

> 管理类 API **不** 携带业务 `X-Workspace-Id` 时走平台 scope；Workspace 设置 API 须 WS_ADMIN+。  
> **认证 API** 见下表 **API-AUTH-***（与 IAM Filter 配合）。

### API-AUTH-01 认证配置与登录

| 项 | 值 |
|----|-----|
| **方法** | `GET` |
| **路径** | `/api/v1/auth/config` |
| **响应** | `{ devMode, registrationEnabled, localLoginEnabled, oidc: { enabled, authorizationEndpoint, clientId } }` |

| 项 | 值 |
|----|-----|
| **方法** | `POST` |
| **路径** | `/api/v1/auth/login` · `/api/v1/auth/register` |
| **Body** | `{ loginName, password }` 或注册字段 |
| **响应** | `AuthTokenDto`（accessToken, userId, …） |

| 项 | 值 |
|----|-----|
| **方法** | `GET` / `POST` |
| **路径** | `/api/v1/auth/oidc/authorize` · `/api/v1/auth/oidc/exchange` |
| **说明** | Authorization code 换 IdP access token；`preferred_username` 须匹配 `app_user.login_name` |

### API-IAM-01 当前用户

| 项 | 值 |
|----|-----|
| **方法** | `GET` |
| **路径** | `/api/v1/iam/me` |
| **响应** | `CurrentUserDto`（userId, displayName, isSuperAdmin, **hasWorkspaces**, workspaces[]） |
| **workspaces** | **仅** `workspace_member` 行；不含未加入的种子 WS |

### API-IAM-02 用户可访问 Workspace 列表

| 项 | 值 |
|----|-----|
| **方法** | `GET` |
| **路径** | `/api/v1/iam/workspaces` |
| **响应** | `WorkspaceMembershipDto[]`（workspaceId, name, role, enabledModules[]） |

### API-IAM-03 Workspace 模块配置

| 项 | 值 |
|----|-----|
| **场景** | SCN-T06b |
| **方法** | `PUT` |
| **路径** | `/api/v1/iam/workspaces/{workspaceId}/modules` |
| **Body** | `{ modules: [{ moduleId, enabled }] }` |
| **权限** | WS_ADMIN 或 OWNER；SUPER_ADMIN |

### API-IAM-04 成员模块权限矩阵

| 项 | 值 |
|----|-----|
| **方法** | `PUT` |
| **路径** | `/api/v1/iam/workspaces/{workspaceId}/members/{userId}/permissions` |
| **Body** | `{ permissions: [{ moduleId, accessLevel: NONE\|VIEW\|EDIT }] }` |
| **权限** | WS_ADMIN 或 OWNER |

### API-IAM-05 平台用户管理（Super Admin）

| 项 | 值 |
|----|-----|
| **场景** | SCN-T06c |
| **方法** | `GET` / `POST` / `PATCH` |
| **路径** | `/api/v1/admin/users` |
| **权限** | SUPER_ADMIN |

### API-IAM-06 Workspace 成员管理

| 项 | 值 |
|----|-----|
| **方法** | `POST` / `DELETE` |
| **路径** | `/api/v1/iam/workspaces/{workspaceId}/members` |
| **Body** | `{ userId, role: MEMBER\|WS_ADMIN }` |
| **权限** | WS_ADMIN 或 OWNER |

**IAM 错误码：** `WORKSPACE_FORBIDDEN` · `MODULE_DISABLED` · `MODULE_FORBIDDEN` · `IAM_FORBIDDEN`

**BusinessRules 写权限：** 按 tab 所属模块校验 — demand/capacity/material → **MOD-OCP** · EDIT；production/labor → **MOD-SCH** · EDIT（§19.4.5）。

---

## API-INT：数据集成（§19 · MOD-DI · TODO-19）

> **门禁：** 须 WS 成员 + **MOD-DI** 启用；写操作须 **EDIT**。适配器密钥走 `credentialRef`，不落库明文。

### API-INT-01 导入批次列表

| 项 | 值 |
|----|-----|
| **ID** | API-INT-01 |
| **场景** | SCN-T07a |
| **方法** | `GET` |
| **路径** | `/api/v1/integration/batches` |
| **Query** | `limit`（默认 20） |
| **响应** | `IntegrationBatchDto[]`（importBatchId, adapterId, sourceSystem, rowCount, qualityStatus, createdAt） |
| **规则** | RULE-MD-02 |

### API-INT-02 External 表清单

| 项 | 值 |
|----|-----|
| **ID** | API-INT-02 |
| **场景** | SCN-T07a |
| **方法** | `GET` |
| **路径** | `/api/v1/integration/external/{domain}/tables` |
| **Path** | `domain` = `master` \| `transactional` |
| **响应** | `ExternalTableInfoDto[]`（tableName, label, rowCount?） |

### API-INT-03 External 行分页

| 项 | 值 |
|----|-----|
| **ID** | API-INT-03 |
| **场景** | SCN-T07a |
| **方法** | `GET` |
| **路径** | `/api/v1/integration/external/{domain}/{table}` |
| **Query** | `page`, `size`, `importBatchId?`, `qualityStatus?` |
| **响应** | 分页 `external_*` 行 + `quality_issue_codes` |
| **规则** | RULE-MD-01（计划路径禁止读本 API 替代 md_*） |

### API-INT-04 适配器状态

| 项 | 值 |
|----|-----|
| **ID** | API-INT-04 |
| **场景** | SCN-T07b |
| **方法** | `GET` |
| **路径** | `/api/v1/integration/adapters` |
| **响应** | `IntegrationAdapterStatusDto[]`（adapterId, enabled, configured, lastRunAt, lastStatus） |

### API-INT-05 触发适配器同步

| 项 | 值 |
|----|-----|
| **ID** | API-INT-05 |
| **场景** | SCN-T07b · SCN-T07c |
| **方法** | `POST` |
| **路径** | `/api/v1/integration/adapters/{adapterId}/run` |
| **权限** | MOD-DI · EDIT |
| **响应** | `{ importBatchId?, status }` |

### API-INT-06 适配器配置

| 项 | 值 |
|----|-----|
| **ID** | API-INT-06 |
| **场景** | SCN-T07b |
| **方法** | `PUT` |
| **路径** | `/api/v1/integration/adapters/{adapterId}/config` |
| **Body** | 适配器 `config_schema` 字段（不含明文密钥） |
| **权限** | MOD-DI · EDIT；WS_ADMIN 推荐 |

### API-INT-07 Excel 上传

| 项 | 值 |
|----|-----|
| **ID** | API-INT-07 |
| **场景** | SCN-T07a |
| **方法** | `POST` |
| **路径** | `/api/v1/integration/adapters/excel/upload` |
| **Body** | `multipart/form-data`：`file`；`validateOnly?` |
| **响应** | `{ importBatchId, rowCount }` |
| **规则** | RULE-MD-02 |

### API-INT-08 质检报告

| 项 | 值 |
|----|-----|
| **ID** | API-INT-08 |
| **场景** | SCN-T07a |
| **方法** | `GET` |
| **路径** | `/api/v1/integration/quality` |
| **Query** | `importBatchId?`, `issueCode?` |
| **响应** | 批次/行级质检摘要 |

**集成错误码：** `ADAPTER_NOT_CONFIGURED` · `IMPORT_VALIDATION_FAILED` · `SYNC_BLOCKED`

---

## API-ADM-01 销售订单缩放

| 项 | 值 |
|----|-----|
| **ID** | API-ADM-01 |
| **标签** | `[CUST][SPECIFIC]` 运维工具 |
| **方法** | `POST` |
| **路径** | `/api/v1/admin/scale-sales-order-demand` |
| **Query** | `divisor`, `replaceWorkOrders` |
| **说明** | 幂等除法；重复执行会二次缩放 |

---

## 已废弃契约

| 路径 | 替代 |
|------|------|
| `GET .../master-plan/diagnostics/preview` | 已移除（ADR-03） |
| `GET .../detail-schedule/diagnostics/preview` | 已移除 |
| `POST .../planning/order-chain/preview` | API-FC-01 + Sandbox optimize |

---

**回指：** [03-scenarios.md](./03-scenarios.md) · [08-acceptance.md](./08-acceptance.md) · [19-workspace-modules-and-adapters.md](../volumes/platform/18-19-workspace-platform.md)
