# 订单推演链（方案 B）Implementation Plan

> **状态：已暂停（2026-05-30）** — Phase 1–2 已完成；Phase 3–7 留待后续讨论。当前优先：**作业排程 / 待排工单**。

## Backlog（暂停项）

- [ ] Phase 3.1：baseline 甘特对比高亮（`compare.nodeDeltas`）
- [ ] Phase 3.2：从「推演诊断」交叉链接到「订单推演」
- [ ] Phase 3.3–3.4：S05 Projector 测试、慢 E2E 测试 tagging
- [ ] Phase 4：文档 §4.2 并行文案、§8 标题修正
- [ ] Phase 5（方案 C）：内存 override（交期、数量、库存）
- [ ] Phase 6：订单 scoped Context（性能）
- [ ] Phase 7：可选增强（MRP 曲线、RESOURCE_SLOT 节点等）

---

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 S04/S05 PlanningContext，为单条销售订单行提供 POST preview API 与前端「订单推演」页，展示全链条可视化与试算推演信号（不求解 Timefold）。

**Architecture:** `OrderPlanningChainService` 构建共享 `MaterialPlanningContext` + 双 Context；`FulfillmentPeggingService` 提供 BOM/peg 拓扑骨架；`OrderPlanningChainProjector` 用 Context 中的 allocations/operations 填充时间窗与 `planningSignals`；前端复用 `FulfillmentChainSyncView`。

**Tech Stack:** Quarkus/Jakarta REST, JUnit 5, React/TypeScript/Vite, 现有 `gantt-task-react`

**Spec:** `docs/superpowers/specs/2026-05-30-order-planning-chain-design.md`

---

## File Map

| File | Responsibility |
|------|----------------|
| `api/dto/planning/OrderPlanningChainPreviewRequest.java` | REST 入参 |
| `api/dto/planning/PlanningSignalDto.java` | 节点推演信号 |
| `api/dto/planning/OrderPlanningChainSummaryDto.java` | 链级摘要 |
| `api/dto/planning/OrderPlanningChainNodeDto.java` | 链节点 |
| `api/dto/planning/OrderPlanningChainNodeDeltaDto.java` | baseline 对比 delta |
| `api/dto/planning/OrderPlanningChainCompareDto.java` | 对比块 |
| `api/dto/planning/OrderPlanningChainDto.java` | REST 出参 |
| `scenario/planning/OrderPlanningChainProjector.java` | Context → DTO 投影 |
| `scenario/planning/OrderPlanningChainService.java` | 编排 build + project |
| `api/PlanningResource.java` | POST 端点 |
| `test/.../OrderPlanningChainProjectorTest.java` | Projector 单元测试 |
| `test/.../OrderPlanningChainResourceTest.java` | REST 冒烟 |
| `frontend/src/types/orderPlanningChain.ts` | TS 类型 |
| `frontend/src/utils/orderPlanningChainGantt.ts` | Context 链 → Gantt tasks |
| `frontend/src/components/PlanningSignalBadge.tsx` | 信号徽章 |
| `frontend/src/components/OrderChainNodeDetail.tsx` | 节点详情侧栏 |
| `frontend/src/pages/OrderPlanningChainPage.tsx` | 推演页 |
| `frontend/src/App.tsx` + `Layout.tsx` | 路由与导航 |
| `docs/aps-planning-layer.md` | §8.5 文档 |

---

## Phase 1 — Backend MVP

### Task 1: Planning DTOs

**Files:**
- Create: `src/main/java/com/plantops/api/dto/planning/PlanningSignalDto.java`
- Create: `src/main/java/com/plantops/api/dto/planning/OrderPlanningChainPreviewRequest.java`
- Create: `src/main/java/com/plantops/api/dto/planning/OrderPlanningChainSummaryDto.java`
- Create: `src/main/java/com/plantops/api/dto/planning/OrderPlanningChainNodeDto.java`
- Create: `src/main/java/com/plantops/api/dto/planning/OrderPlanningChainNodeDeltaDto.java`
- Create: `src/main/java/com/plantops/api/dto/planning/OrderPlanningChainCompareDto.java`
- Create: `src/main/java/com/plantops/api/dto/planning/OrderPlanningChainDto.java`

- [ ] **Step 1: Create `PlanningSignalDto`**

```java
package com.plantops.api.dto.planning;

public record PlanningSignalDto(
        String severity,
        String reasonCode,
        String message,
        String entityId
) {
}
```

- [ ] **Step 2: Create request record**

```java
package com.plantops.api.dto.planning;

import java.time.LocalDate;

public record OrderPlanningChainPreviewRequest(
        String salesOrderNo,
        int salesOrderLineNo,
        String masterPlanStrategyId,
        Boolean useFeedbackOverlay,
        LocalDate feedbackCutoff,
        String detailScheduleMasterPlanVersionId,
        String baselineMasterPlanVersionId
) {
}
```

- [ ] **Step 3: Create summary + node records**

```java
// OrderPlanningChainSummaryDto.java
package com.plantops.api.dto.planning;

import java.time.Instant;
import java.util.Map;

public record OrderPlanningChainSummaryDto(
        String capacityStrategy,
        String inventorySnapshotId,
        int workOrderCount,
        int operationCount,
        Map<String, Integer> issueCountBySeverity,
        Instant computedAt
) {
}
```

```java
// OrderPlanningChainNodeDto.java
package com.plantops.api.dto.planning;

import com.plantops.api.dto.FulfillmentOperationDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record OrderPlanningChainNodeDto(
        String nodeId,
        String nodeType,
        String laneId,
        String label,
        String status,
        int depth,
        String productCode,
        BigDecimal quantity,
        LocalDate windowStart,
        LocalDate windowEnd,
        String planningLayer,
        List<PlanningSignalDto> planningSignals,
        Map<String, Object> attributes,
        List<FulfillmentOperationDto> operations
) {
}
```

- [ ] **Step 4: Create compare + root DTO**

```java
// OrderPlanningChainNodeDeltaDto.java
public record OrderPlanningChainNodeDeltaDto(
        String nodeId,
        LocalDate baselineStart,
        LocalDate baselineEnd,
        LocalDate trialStart,
        LocalDate trialEnd,
        boolean statusChanged
) {
}

// OrderPlanningChainCompareDto.java
public record OrderPlanningChainCompareDto(
        String baselineVersionId,
        List<OrderPlanningChainNodeDeltaDto> nodeDeltas
) {
}

// OrderPlanningChainDto.java — import FulfillmentPegEdgeDto
public record OrderPlanningChainDto(
        String salesOrderNo,
        int salesOrderLineNo,
        String productCode,
        java.time.LocalDate dueDate,
        java.time.LocalDate promiseDate,
        String overallStatus,
        String kittingStatus,
        OrderPlanningChainSummaryDto summary,
        List<OrderPlanningChainNodeDto> nodes,
        List<com.plantops.api.dto.FulfillmentPegEdgeDto> edges,
        OrderPlanningChainCompareDto compare
) {
}
```

- [ ] **Step 5: Compile**

Run: `cd plant-operation-plan && .\mvnw.cmd -q compile`
Expected: BUILD SUCCESS

---

### Task 2: OrderPlanningChainProjector (TDD)

**Files:**
- Create: `src/main/java/com/plantops/scenario/planning/OrderPlanningChainProjector.java`
- Create: `src/test/java/com/plantops/scenario/planning/OrderPlanningChainProjectorTest.java`

- [ ] **Step 1: Write failing test — eligible window from allocations**

```java
package com.plantops.scenario.planning;

import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.FulfillmentPegEdgeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainDto;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderPlanningChainProjectorTest {

    @Test
    void workOrderNodeUsesEligibleSlotDateRange() {
        OrderAllocation alloc = allocation(
                "WO-A@OP10_0#0", "WO-A", "SO1", 1, "PROD-A", "RES-1", 10);
        alloc.setEligibleTimeSlots(List.of(
                slot("S1", "RES-1", LocalDate.of(2026, 6, 1)),
                slot("S2", "RES-1", LocalDate.of(2026, 6, 5))));

        OrderFulfillmentChainDto topology = topologyWithWorkOrder("WO-A", "n-wo-a");
        MasterPlanPlanningContext mpCtx = minimalContext(List.of(alloc));

        OrderPlanningChainDto result = OrderPlanningChainProjector.project(
                topology, mpCtx, null, List.of("WO-A"));

        OrderPlanningChainNodeDto woNode = result.nodes().stream()
                .filter(n -> "WO-A".equals(n.attributes().get("workOrderNo")))
                .findFirst()
                .orElseThrow();
        assertEquals(LocalDate.of(2026, 6, 1), woNode.windowStart());
        assertEquals(LocalDate.of(2026, 6, 5), woNode.windowEnd());
        assertEquals("OK", woNode.status());
    }

    @Test
    void blockedWhenNoEligibleSlots() {
        OrderAllocation alloc = allocation(
                "WO-A@OP10_0#0", "WO-A", "SO1", 1, "PROD-A", "RES-1", 10);
        alloc.setEligibleTimeSlots(List.of());

        OrderFulfillmentChainDto topology = topologyWithWorkOrder("WO-A", "n-wo-a");
        MasterPlanPlanningContext mpCtx = minimalContext(List.of(alloc));

        OrderPlanningChainDto result = OrderPlanningChainProjector.project(
                topology, mpCtx, null, List.of("WO-A"));

        OrderPlanningChainNodeDto woNode = result.nodes().stream()
                .filter(n -> "WORK_ORDER".equals(n.nodeType()))
                .findFirst()
                .orElseThrow();
        assertEquals("BLOCKED", woNode.status());
        assertTrue(woNode.planningSignals().stream()
                .anyMatch(s -> "ALLOC_NO_RESOURCE_SLOTS".equals(s.reasonCode())));
    }

    // --- helpers: allocation(), slot(), topologyWithWorkOrder(), minimalContext()
}
```

Implement helpers in test file using package-visible factory methods or inline builders (mirror `MasterPlanParallelBindingServiceTest.alloc` pattern).

- [ ] **Step 2: Run test — expect FAIL**

Run: `.\mvnw.cmd -q test -Dtest=OrderPlanningChainProjectorTest`
Expected: FAIL — class `OrderPlanningChainProjector` not found

- [ ] **Step 3: Implement minimal `OrderPlanningChainProjector`**

```java
package com.plantops.scenario.planning;

import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.FulfillmentPegEdgeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.planning.*;
import com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class OrderPlanningChainProjector {

    public static OrderPlanningChainDto project(
            OrderFulfillmentChainDto topology,
            MasterPlanPlanningContext mpCtx,
            DetailSchedulePlanningContext dsCtx,
            List<String> chainWorkOrderNos) {
        return project(topology, mpCtx, dsCtx, chainWorkOrderNos, null, null);
    }

    public static OrderPlanningChainDto project(
            OrderFulfillmentChainDto topology,
            MasterPlanPlanningContext mpCtx,
            DetailSchedulePlanningContext dsCtx,
            List<String> chainWorkOrderNos,
            String baselineVersionId,
            BaselineWindowResolver baselineResolver) {
        Set<String> woSet = new LinkedHashSet<>(chainWorkOrderNos);
        Map<String, List<OrderAllocation>> allocByWo = mpCtx.orderAllocations().stream()
                .filter(a -> woSet.contains(a.getWorkOrderNo()))
                .collect(Collectors.groupingBy(OrderAllocation::getWorkOrderNo));

        Map<String, List<OperationAssignment>> opByWo = dsCtx != null
                ? dsCtx.operations().stream()
                        .filter(o -> woSet.contains(o.getWorkOrderNo()))
                        .collect(Collectors.groupingBy(OperationAssignment::getWorkOrderNo))
                : Map.of();

        List<OrderPlanningChainNodeDto> nodes = new ArrayList<>();
        for (FulfillmentChainNodeDto src : topology.nodes()) {
            nodes.add(mapNode(src, allocByWo, opByWo, mpCtx, baselineVersionId, baselineResolver));
        }

        String overall = aggregateStatus(nodes);
        OrderPlanningChainSummaryDto summary = buildSummary(mpCtx, dsCtx, woSet, nodes);

        OrderPlanningChainCompareDto compare = baselineVersionId != null && baselineResolver != null
                ? buildCompare(baselineVersionId, nodes, baselineResolver)
                : null;

        return new OrderPlanningChainDto(
                topology.salesOrderNo(),
                topology.salesOrderLineNo(),
                topology.productCode(),
                topology.dueDate(),
                topology.promiseDate(),
                overall,
                topology.kittingStatus(),
                summary,
                nodes,
                topology.edges(),
                compare);
    }

    /** Package-visible for tests; maps one topology node. */
    static OrderPlanningChainNodeDto mapNode(/* ... */) { /* see Step 3 full impl */ }

    static LocalDate minSlotDate(List<OrderAllocation> allocs) { /* eligible min */ }
    static LocalDate maxSlotDate(List<OrderAllocation> allocs) { /* eligible max */ }
    static List<PlanningSignalDto> signalsForWorkOrder(/* issues filter */) { /* ... */ }

    @FunctionalInterface
    public interface BaselineWindowResolver {
        LocalDate[] resolve(String workOrderNo); // [start, end] or null
    }
}
```

**`mapNode` rules (implement fully):**

| nodeType | window | status |
|----------|--------|--------|
| `WORK_ORDER` | min/max eligible across WO allocations | BLOCKED if any alloc has empty eligible; WARN if signals present |
| `SALES_ORDER` | min(windowStart of children) .. dueDate | WARN if overall at risk |
| `MATERIAL` | first shortage date from `MaterialFeasibilityContext.closingOn` scan | WARN if any date &lt; 0 before horizon end |
| other | keep peg label; window from children or null | inherit worst child |

Attach `planningLayer`: `PEG` for SO/MATERIAL, `S04` for WO when allocations exist, `S05` when dsCtx operations mapped.

For `WORK_ORDER`, set `attributes.workOrderNo`, `eligibleSlotCount`, `allocationCount`.

- [ ] **Step 4: Run tests — expect PASS**

Run: `.\mvnw.cmd -q test -Dtest=OrderPlanningChainProjectorTest`
Expected: BUILD SUCCESS

---

### Task 3: OrderPlanningChainService

**Files:**
- Create: `src/main/java/com/plantops/scenario/planning/OrderPlanningChainService.java`
- Modify: `src/main/java/com/plantops/scenario/MasterPlanService.java` — no change if public methods suffice
- Modify: `src/main/java/com/plantops/scenario/DetailScheduleService.java` — expose `buildPlanningContext(mpId, material)` if not public

- [ ] **Step 1: Implement service**

```java
package com.plantops.scenario.planning;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainPreviewRequest;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.scenario.*;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OrderPlanningChainService {

    @Inject MasterPlanStrategyConfigService strategyConfigService;
    @Inject MasterPlanService masterPlanService;
    @Inject DetailScheduleService detailScheduleService;
    @Inject FulfillmentPeggingService fulfillmentPeggingService;
    @Inject MaterialPlanningContextBuilder materialPlanningContextBuilder;
    @Inject SampleDataLoader sampleDataLoader;

    public OrderPlanningChainDto preview(OrderPlanningChainPreviewRequest req) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(req.salesOrderNo(), req.salesOrderLineNo());
        if (order == null) {
            throw new NotFoundException("Sales order line not found");
        }
        sampleDataLoader.extendCalendarsToHorizon();

        var resolved = strategyConfigService.resolve(blankToNull(req.masterPlanStrategyId()));
        MasterPlanCapacityOverlay overlay = Boolean.TRUE.equals(req.useFeedbackOverlay())
                ? masterPlanService.buildFeedbackOverlay(req.feedbackCutoff() != null ? req.feedbackCutoff() : LocalDate.now())
                : MasterPlanCapacityOverlay.empty();

        MaterialPlanningContext material = materialPlanningContextBuilder.build();
        MasterPlanPlanningContext mpCtx = masterPlanService.buildPlanningContext(resolved, overlay, material);

        DetailSchedulePlanningContext dsCtx = null;
        if (req.detailScheduleMasterPlanVersionId() != null && !req.detailScheduleMasterPlanVersionId().isBlank()) {
            dsCtx = detailScheduleService.buildPlanningContext(req.detailScheduleMasterPlanVersionId(), material);
        }

        String kittingStatus = KittingResultEntity.findForLine(order.salesOrderNo, order.salesOrderLineNo)
                .map(k -> k.kittingStatus)
                .orElse("UNKNOWN");

        OrderFulfillmentChainDto topology = fulfillmentPeggingService.build(order, kittingStatus, null);

        List<String> workOrderNos = extractWorkOrderNos(topology);

        OrderPlanningChainProjector.BaselineWindowResolver baseline = null;
        String baselineId = blankToNull(req.baselineMasterPlanVersionId());
        if (baselineId != null) {
            baseline = wo -> {
                var w = masterPlanService.resolveWorkOrderWindow(baselineId, wo);
                return w == null ? null : new LocalDate[] {
                        w.plannedStart().toLocalDate(),
                        w.plannedEnd().toLocalDate()
                };
            };
        }

        return OrderPlanningChainProjector.project(
                topology, mpCtx, dsCtx, workOrderNos, baselineId, baseline);
    }

    private static List<String> extractWorkOrderNos(OrderFulfillmentChainDto topology) {
        List<String> wos = new ArrayList<>();
        for (var n : topology.nodes()) {
            if ("WORK_ORDER".equals(n.nodeType())) {
                Object wo = n.attributes().get("workOrderNo");
                if (wo != null) {
                    wos.add(wo.toString());
                }
            }
        }
        return wos;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
```

Add `SalesOrderLineEntity.findByKey` if missing — or use existing finder pattern from `DemandService`.

Filter `mpCtx.diagnostics().issues()` into node signals by matching `workOrderNo` / `entityId` inside `OrderPlanningChainProjector.signalsForWorkOrder`.

- [ ] **Step 2: Compile**

Run: `.\mvnw.cmd -q compile`
Expected: BUILD SUCCESS

---

### Task 4: REST endpoint

**Files:**
- Modify: `src/main/java/com/plantops/api/PlanningResource.java`

- [ ] **Step 1: Add inject + endpoint**

```java
@Inject
OrderPlanningChainService orderPlanningChainService;

@POST
@Path("/planning/order-chain/preview")
@Consumes(MediaType.APPLICATION_JSON)
public OrderPlanningChainDto previewOrderPlanningChain(OrderPlanningChainPreviewRequest request) {
    return orderPlanningChainService.preview(request);
}
```

- [ ] **Step 2: REST smoke test**

Create: `src/test/java/com/plantops/api/OrderPlanningChainResourceTest.java`

```java
@QuarkusTest
class OrderPlanningChainResourceTest {

    @Test
    void previewReturns200ForSampleOrder() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Workspace-Id", "default")
            .body("""
                {"salesOrderNo":"%s","salesOrderLineNo":1}
                """.formatted(sampleSalesOrderNo()))
        .when()
            .post("/api/v1/planning/order-chain/preview")
        .then()
            .statusCode(200)
            .body("overallStatus", notNullValue())
            .body("nodes.size()", greaterThan(0));
    }
}
```

Use actual sample order no from `SampleDataLoader` / existing test fixtures (`DemandResourceTest` pattern).

Run: `.\mvnw.cmd -q test -Dtest=OrderPlanningChainResourceTest`
Expected: PASS

---

## Phase 2 — Frontend

### Task 5: Types and API client

**Files:**
- Create: `frontend/src/types/orderPlanningChain.ts`
- Modify: `frontend/src/api/client.ts`

- [ ] **Step 1: Add TypeScript types**

```typescript
// frontend/src/types/orderPlanningChain.ts
import type { FulfillmentPegEdge } from './api';

export interface PlanningSignal {
  severity: 'INFO' | 'WARN' | 'SKIP';
  reasonCode: string;
  message: string;
  entityId: string | null;
}

export interface OrderPlanningChainNode {
  nodeId: string;
  nodeType: string;
  laneId: string;
  label: string;
  status: string;
  depth: number;
  productCode: string;
  quantity: number;
  windowStart: string | null;
  windowEnd: string | null;
  planningLayer: string;
  planningSignals: PlanningSignal[];
  attributes: Record<string, unknown>;
  operations: import('./api').FulfillmentOperation[];
}

export interface OrderPlanningChain {
  salesOrderNo: string;
  salesOrderLineNo: number;
  productCode: string;
  dueDate: string;
  promiseDate: string | null;
  overallStatus: string;
  kittingStatus: string;
  summary: {
    capacityStrategy: string | null;
    inventorySnapshotId: string | null;
    workOrderCount: number;
    operationCount: number;
    issueCountBySeverity: Record<string, number>;
    computedAt: string;
  };
  nodes: OrderPlanningChainNode[];
  edges: FulfillmentPegEdge[];
  compare: {
    baselineVersionId: string;
    nodeDeltas: Array<{
      nodeId: string;
      baselineStart: string | null;
      baselineEnd: string | null;
      trialStart: string | null;
      trialEnd: string | null;
      statusChanged: boolean;
    }>;
  } | null;
}

export interface OrderPlanningChainPreviewRequest {
  salesOrderNo: string;
  salesOrderLineNo: number;
  masterPlanStrategyId?: string;
  useFeedbackOverlay?: boolean;
  feedbackCutoff?: string;
  detailScheduleMasterPlanVersionId?: string;
  baselineMasterPlanVersionId?: string;
}
```

- [ ] **Step 2: Add API method**

```typescript
// in client.ts
previewOrderPlanningChain: (body: OrderPlanningChainPreviewRequest) =>
  request<OrderPlanningChain>('/api/v1/planning/order-chain/preview', {
    method: 'POST',
    body: JSON.stringify(body),
  }),
```

- [ ] **Step 3: Build frontend**

Run: `cd frontend && npm run build`
Expected: SUCCESS

---

### Task 6: Gantt adapter

**Files:**
- Create: `frontend/src/utils/orderPlanningChainGantt.ts`

- [ ] **Step 1: Implement task mapper**

Use `windowStart` / `windowEnd` (ISO date strings) → `Date` at 08:00–17:00 (match `fulfillmentGantt.ts` WORKDAY convention).

```typescript
import type { Task } from 'gantt-task-react';
import type { OrderPlanningChainNode } from '../types/orderPlanningChain';

export function orderPlanningChainToGanttTasks(nodes: OrderPlanningChainNode[]): Task[] {
  return nodes
    .filter((n) => n.windowStart && n.windowEnd)
    .map((n) => ({
      id: n.nodeId,
      name: n.label,
      type: 'task',
      start: parseWorkdayStart(n.windowStart!),
      end: parseWorkdayEnd(n.windowEnd!),
      progress: n.status === 'OK' ? 100 : n.status === 'WARN' ? 50 : 0,
      project: undefined,
      styles: statusStyles(n.status),
    }));
}
```

Export `statusStyles` mapping: OK → green tint, WARN → amber, BLOCKED → red (match existing badge CSS vars).

- [ ] **Step 2: Unit smoke** — optional manual via page; no Jest in project.

---

### Task 7: UI components

**Files:**
- Create: `frontend/src/components/PlanningSignalBadge.tsx`
- Create: `frontend/src/components/OrderChainNodeDetail.tsx`
- Create: `frontend/src/components/PlanningSignalBadge.css` (minimal)

- [ ] **Step 1: `PlanningSignalBadge`**

Reuse `reasonLabel()` from `planningDiagnosticsModel.ts`:

```tsx
export function PlanningSignalBadge({ signal }: { signal: PlanningSignal }) {
  const cls = signal.severity === 'SKIP' ? 'danger' : signal.severity === 'WARN' ? 'warn' : 'info';
  return (
    <span className={`opsig-badge opsig-${cls}`} title={signal.message}>
      {reasonLabel(signal.reasonCode)}
    </span>
  );
}
```

- [ ] **Step 2: `OrderChainNodeDetail`**

Show: nodeType, status, planningLayer, window range, `planningSignals` list, key attributes (`eligibleSlotCount`, `workOrderNo`), operations table if present.

---

### Task 8: OrderPlanningChainPage

**Files:**
- Create: `frontend/src/pages/OrderPlanningChainPage.tsx`
- Create: `frontend/src/pages/OrderPlanningChainPage.css`

- [ ] **Step 1: Page layout**

Pattern after `DemandPage.tsx`:

- Top: `PageHeader` + `usePlan()` for `activePlanVersionId`, `masterPlan.strategyId`
- Toolbar: order picker (load `api.demandPool`), checkbox feedback overlay, date input, baseline version dropdown (optional), 「刷新试算」button
- Split: left `OrderChainNodeDetail` / node list; right `FulfillmentChainSyncView` with `orderPlanningChainToGanttTasks`
- Status banner: `overallStatus`, `summary.capacityStrategy`, issue counts
- Link button → 「正式求解」navigates to `/master-plan/plan-run` or calls `api.solveMasterPlan`

- [ ] **Step 2: Wire preview on selection + param change**

```typescript
const runPreview = useCallback(async () => {
  if (!selected) return;
  setChain(await api.previewOrderPlanningChain({
    salesOrderNo: selected.salesOrderNo,
    salesOrderLineNo: selected.salesOrderLineNo,
    masterPlanStrategyId: strategyId ?? undefined,
    useFeedbackOverlay,
    feedbackCutoff: useFeedbackOverlay ? feedbackCutoff : undefined,
    detailScheduleMasterPlanVersionId: activePlanVersionId ?? undefined,
    baselineMasterPlanVersionId: baselineVersionId ?? undefined,
  }));
}, [selected, strategyId, useFeedbackOverlay, feedbackCutoff, activePlanVersionId, baselineVersionId]);
```

---

### Task 9: Routing and navigation

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/Layout.tsx`

- [ ] **Step 1: Add route**

```tsx
<Route path="master-plan/analysis/order-chain" element={<OrderPlanningChainPage />} />
```

- [ ] **Step 2: Add nav item under 计划分析**

```tsx
{ to: '/master-plan/analysis/order-chain', label: '订单推演' },
```

Place after「推演诊断」.

- [ ] **Step 3: Full build**

Run: `cd frontend && npm run build && cd .. && .\mvnw.cmd -q test`
Expected: PASS

---

## Phase 3 — S05 signals + baseline UI (same plan, after Phase 2)

### Task 10: S05 operation signals in Projector

**Files:**
- Modify: `OrderPlanningChainProjector.java`
- Modify: `OrderPlanningChainProjectorTest.java`

- [ ] **Step 1: Test kitting + mp contract fallback**

```java
@Test
void s05OperationKittingBlocked() {
    OperationAssignment op = new OperationAssignment();
    op.setWorkOrderNo("WO-A");
    op.setOperationId("op-1");
    op.setKittingEligible(false);
    // ... attach to dsCtx, assert WORK_ORDER or OPERATION node BLOCKED + WO_KITTING_SHORT
}
```

- [ ] **Step 2: Implement**

For each WO, expand `operations` list on node from S05 `OperationAssignment`:
- `kittingEligible=false` → signal `WO_KITTING_SHORT`, status BLOCKED
- missing contract but `mpTargetEndDate` → `OP_MP_TARGET_FALLBACK` WARN
- window from contract dates when present

- [ ] **Step 3: Run tests**

Run: `.\mvnw.cmd -q test -Dtest=OrderPlanningChainProjectorTest`

---

### Task 11: Baseline compare highlighting

**Files:**
- Modify: `OrderPlanningChainPage.tsx`
- Modify: `orderPlanningChainGantt.ts`

- [ ] **Step 1: When `chain.compare` present, render second ghost bar or border on tasks where `nodeDeltas.statusChanged` or date diff &gt; 0

- [ ] **Step 2: Legend** — 「试算窗」vs「基准求解窗」

---

## Phase 4 — Documentation

### Task 12: Update aps-planning-layer.md

**Files:**
- Modify: `docs/aps-planning-layer.md`

- [ ] **Step 1: Add §8.5 订单推演链** — API path, DTO 摘要, 前端入口, 与满足链区别

- [ ] **Step 2: Fix §4.2 stale parallel sentence** (from prior backlog)

- [ ] **Step 3: Update §8 section title** to「扩展点（已实现 + 订单推演链）」或类似

---

## Spec Coverage Self-Check

| Spec requirement | Task |
|------------------|------|
| POST preview API | Task 4 |
| Dual Context build | Task 3 |
| Peg topology + Context projection | Task 2–3 |
| planningSignals | Task 2, 10 |
| eligible window min/max | Task 2 |
| overall OK/AT_RISK/BLOCKED | Task 2 |
| baseline compare | Task 3, 11 |
| Frontend推演页 | Task 8–9 |
| Reuse FulfillmentChainSyncView | Task 8 |
| §8.5 docs | Task 12 |
| v1 no memory overrides | N/A (explicitly out of scope) |

---

## Verification Checklist (before merge)

- [ ] `.\mvnw.cmd test` green
- [ ] `cd frontend && npm run build` green
- [ ] Manual: 计划分析 → 订单推演 → 选订单 → 刷新试算 → 甘特条与信号显示
- [ ] Manual: 改策略或 overlay → 试算结果变化
- [ ] Manual: 选 baseline 版本 → compare deltas 显示

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-30-order-planning-chain.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 按 Task 派发子 agent，每 Task 完成后 review
2. **Inline Execution** — 本会话按 Task 1→12 连续实现，Phase 1 完成后 checkpoint

**Which approach?**
