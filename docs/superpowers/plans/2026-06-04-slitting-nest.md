# 分切排样（Slitting Nest）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Plant Operation Plan 中交付独立的分切排样模块（P0–P3）：母卷/子订单 CRUD、Timefold 两阶段分层求解、Konva 交互画板与实时 KPI。

**Architecture:** L1 Flyway 持久化 → L2 `SlittingPlanningContext` → L3 两阶段 `SlittingNestSolution`（Phase1 Child→Intermediate，Phase2 Intermediate→Master）；前端 Zustand + react-konva 画板通过 REST 加载/保存 assignments。

**Tech Stack:** Java 21, Quarkus 3.17, Timefold 2.0, Hibernate Panache, Flyway V49, React 18, TypeScript, Vite, konva, react-konva, zustand

**Spec:** `docs/superpowers/specs/2026-06-04-slitting-nest-design.md`

**Recommended worktree:** 使用 `using-git-worktrees` 在独立分支 `feature/slitting-nest` 上执行本计划。

---

## File Map

| File | Responsibility |
|------|----------------|
| `src/main/resources/db/migration/V49__slitting_nest.sql` | 全部 slitting 表 |
| `src/main/resources/db/migration/V49_1__slitting_nest_demo.sql` | Demo 种子数据 |
| `persistence/entity/MasterRollEntity.java` | 母卷 |
| `persistence/entity/ChildSlittingOrderEntity.java` | 子订单（含 APS 可空外键） |
| `persistence/entity/IntermediateRollCatalogEntity.java` | 标准中间卷规格 |
| `persistence/entity/SlittingPlanVersionEntity.java` | 方案版本 |
| `persistence/entity/SlittingRollNodeEntity.java` | 树节点 |
| `persistence/entity/SlittingAssignmentEntity.java` | 二维分配 |
| `solver/slitting/RollType.java` | MASTER / INTERMEDIATE / CHILD |
| `solver/slitting/CuttingMethod.java` | LONGITUDINAL / TRANSVERSE / LASER |
| `solver/slitting/Dimensions.java` | width/length/thickness 值对象 |
| `solver/slitting/RollNode.java` | 树节点（内存） |
| `solver/slitting/NestAssignment.java` | `@PlanningEntity` 二维放置 |
| `solver/slitting/SlittingNestSolution.java` | `@PlanningSolution`（单阶段求解入口） |
| `solver/slitting/SlittingProblemFacts.java` | 刀缝、层级权重、catalog |
| `solver/slitting/SlittingGeometryUtil.java` | AABB、有效宽高（含旋转） |
| `solver/slitting/SlittingConstraintProvider.java` | 硬/软 Constraint Streams |
| `solver/slitting/SlittingConstructionHeuristic.java` | FFD 初始解 |
| `scenario/slitting/SlittingPlanningContext.java` | 推演快照 |
| `scenario/slitting/SlittingPlanningContextBuilder.java` | 从 DB 构建 Context |
| `scenario/slitting/SlittingProblemMapper.java` | Context → Phase1/Phase2 Solution |
| `scenario/slitting/SlittingLayeredSolverPipeline.java` | 两阶段 Pipeline |
| `scenario/slitting/SlittingPlanService.java` | 创建/求解/持久化/加载 |
| `scenario/slitting/SlittingUtilizationCalculator.java` | 利用率计算 |
| `scenario/slitting/MasterRollService.java` | 母卷 CRUD |
| `scenario/slitting/ChildSlittingOrderService.java` | 子订单 CRUD |
| `scenario/slitting/IntermediateCatalogService.java` | 中间卷规格 CRUD |
| `config/SolverRuntimeFactory.java` | 新增 slitting solver 工厂方法 |
| `config/ParameterRegistry.java` | `slitting_solver_seconds` 默认 30 |
| `api/SlittingMasterRollResource.java` | `/api/v1/slitting/master-rolls` |
| `api/SlittingChildOrderResource.java` | `/api/v1/slitting/child-orders` |
| `api/SlittingIntermediateCatalogResource.java` | `/api/v1/slitting/intermediate-catalog` |
| `api/SlittingPlanResource.java` | `/api/v1/slitting/plans` |
| `api/dto/slitting/*.java` | REST DTO records |
| `frontend/src/types/slitting.ts` | TS 类型 |
| `frontend/src/api/slittingClient.ts` | API 封装 |
| `frontend/src/store/slitting/workbenchStore.ts` | Zustand store |
| `frontend/src/utils/slitting/satCollision.ts` | SAT/AABB 碰撞 |
| `frontend/src/utils/slitting/kpi.ts` | 利用率计算 |
| `frontend/src/components/slitting/*.tsx` | 画板组件 |
| `frontend/src/pages/slitting/*.tsx` | 三个页面 |
| `frontend/src/App.tsx` | 路由 |
| `frontend/src/components/Layout.tsx` | 导航分组 |

---

## Sprint S1 — P0：持久化 + CRUD + Demo

### Task 1: Flyway 表结构

**Files:**
- Create: `src/main/resources/db/migration/V49__slitting_nest.sql`

- [ ] **Step 1: 编写 migration SQL**

```sql
-- V49__slitting_nest.sql
CREATE TABLE master_roll (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    roll_code VARCHAR(128) NOT NULL,
    width_mm DECIMAL(18, 4) NOT NULL,
    length_mm DECIMAL(18, 4) NOT NULL,
    thickness_mm DECIMAL(18, 4),
    material_code VARCHAR(64),
    kerf_longitudinal_mm DECIMAL(18, 4) NOT NULL DEFAULT 0,
    kerf_transverse_mm DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_master_roll_ws_code UNIQUE (workspace_id, roll_code)
);

CREATE TABLE child_slitting_order (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    order_code VARCHAR(128) NOT NULL,
    width_mm DECIMAL(18, 4) NOT NULL,
    length_mm DECIMAL(18, 4) NOT NULL,
    thickness_mm DECIMAL(18, 4),
    quantity INT NOT NULL DEFAULT 1,
    priority INT NOT NULL DEFAULT 0,
    sales_order_no VARCHAR(128),
    sales_order_line_no INT,
    work_order_no VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_child_slitting_order_ws_code UNIQUE (workspace_id, order_code)
);
CREATE INDEX idx_child_slitting_order_so ON child_slitting_order (workspace_id, sales_order_no, sales_order_line_no);
CREATE INDEX idx_child_slitting_order_wo ON child_slitting_order (workspace_id, work_order_no);

CREATE TABLE intermediate_roll_catalog (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    spec_code VARCHAR(128) NOT NULL,
    width_mm DECIMAL(18, 4) NOT NULL,
    length_mm DECIMAL(18, 4) NOT NULL,
    cutting_method VARCHAR(32) NOT NULL,
    kerf_mm DECIMAL(18, 4) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_intermediate_catalog_ws_code UNIQUE (workspace_id, spec_code)
);

CREATE TABLE slitting_plan_version (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    name VARCHAR(256),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    score VARCHAR(64),
    utilization_pct DECIMAL(8, 4),
    solve_duration_ms BIGINT,
    solver_phase VARCHAR(32),
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_slitting_plan_version_ws_id UNIQUE (workspace_id, plan_version_id)
);

CREATE TABLE slitting_plan_master_roll (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    master_roll_id BIGINT NOT NULL
);

CREATE TABLE slitting_plan_child_order (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    child_slitting_order_id BIGINT NOT NULL
);

CREATE TABLE slitting_roll_node (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    parent_node_id VARCHAR(64),
    width_mm DECIMAL(18, 4) NOT NULL,
    length_mm DECIMAL(18, 4) NOT NULL,
    thickness_mm DECIMAL(18, 4),
    cutting_method VARCHAR(32),
    kerf_mm DECIMAL(18, 4),
    source_spec_code VARCHAR(128),
    source_child_order_id BIGINT,
    source_master_roll_id BIGINT,
    CONSTRAINT uk_slitting_roll_node UNIQUE (workspace_id, plan_version_id, node_id)
);

CREATE TABLE slitting_assignment (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    assignment_id VARCHAR(64) NOT NULL,
    child_node_id VARCHAR(64) NOT NULL,
    parent_node_id VARCHAR(64) NOT NULL,
    pos_x_mm DECIMAL(18, 4) NOT NULL,
    pos_y_mm DECIMAL(18, 4) NOT NULL,
    rotated BOOLEAN NOT NULL DEFAULT FALSE,
    sequence INT,
    CONSTRAINT uk_slitting_assignment UNIQUE (workspace_id, plan_version_id, assignment_id)
);
```

- [ ] **Step 2: 验证 migration**

Run: `./mvnw -q test -Dtest=PlantOperationPlanResourceTest`
Expected: BUILD SUCCESS（Flyway 应用 V49 无报错）

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V49__slitting_nest.sql
git commit -m "feat(slitting): add V49 slitting nest schema"
```

---

### Task 2: JPA 实体

**Files:**
- Create: `src/main/java/com/plantops/persistence/entity/MasterRollEntity.java`
- Create: `src/main/java/com/plantops/persistence/entity/ChildSlittingOrderEntity.java`
- Create: `src/main/java/com/plantops/persistence/entity/IntermediateRollCatalogEntity.java`
- Create: `src/main/java/com/plantops/persistence/entity/SlittingPlanVersionEntity.java`
- Create: `src/main/java/com/plantops/persistence/entity/SlittingRollNodeEntity.java`
- Create: `src/main/java/com/plantops/persistence/entity/SlittingAssignmentEntity.java`

- [ ] **Step 1: 创建 MasterRollEntity（其余实体同模式）**

```java
package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "master_roll", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "roll_code"}))
public class MasterRollEntity extends WorkspaceScopedEntity {

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
    public String status = "AVAILABLE";

    @Column(name = "created_ts", nullable = false)
    public LocalDateTime createdTs = LocalDateTime.now();

    public static MasterRollEntity findByRollCode(String rollCode) {
        return find("workspaceId = ?1 and rollCode = ?2", ws(), rollCode).firstResult();
    }

    public static java.util.List<MasterRollEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
```

- [ ] **Step 2: ChildSlittingOrderEntity 含可空 APS 字段**

字段：`salesOrderNo`, `salesOrderLineNo`, `workOrderNo` 均不加 `nullable = false`。

- [ ] **Step 3: SlittingPlanVersionEntity.findByPlanVersionId(String id)**

- [ ] **Step 4: 编译**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

---

### Task 3: DTO + 主数据 Service + REST

**Files:**
- Create: `src/main/java/com/plantops/api/dto/slitting/MasterRollDto.java` 等 record
- Create: `src/main/java/com/plantops/scenario/slitting/MasterRollService.java`
- Create: `src/main/java/com/plantops/scenario/slitting/ChildSlittingOrderService.java`
- Create: `src/main/java/com/plantops/scenario/slitting/IntermediateCatalogService.java`
- Create: `src/main/java/com/plantops/api/SlittingMasterRollResource.java`
- Create: `src/main/java/com/plantops/api/SlittingChildOrderResource.java`
- Create: `src/main/java/com/plantops/api/SlittingIntermediateCatalogResource.java`

- [ ] **Step 1: MasterRollDto record**

```java
package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record MasterRollDto(
        String rollCode,
        BigDecimal widthMm,
        BigDecimal lengthMm,
        BigDecimal thicknessMm,
        String materialCode,
        BigDecimal kerfLongitudinalMm,
        BigDecimal kerfTransverseMm,
        String status) {}
```

- [ ] **Step 2: MasterRollService CRUD**

```java
@ApplicationScoped
public class MasterRollService {
    public List<MasterRollDto> list() { ... }
    @Transactional
    public MasterRollDto create(MasterRollDto dto) {
        if (MasterRollEntity.findByRollCode(dto.rollCode()) != null) {
            throw new BadRequestException("rollCode already exists");
        }
        MasterRollEntity e = new MasterRollEntity();
        e.stampWorkspace();
        e.rollCode = dto.rollCode();
        e.widthMm = dto.widthMm();
        e.lengthMm = dto.lengthMm();
        // ... map remaining fields
        e.persist();
        return toDto(e);
    }
}
```

- [ ] **Step 3: REST 路径统一前缀**

```java
@Path("/api/v1/slitting/master-rolls")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SlittingMasterRollResource {
    @Inject MasterRollService service;
    @GET public List<MasterRollDto> list() { return service.list(); }
    @POST public MasterRollDto create(MasterRollDto dto) { return service.create(dto); }
    @PUT @Path("/{rollCode}") public MasterRollDto update(@PathParam("rollCode") String code, MasterRollDto dto) { ... }
    @DELETE @Path("/{rollCode}") public void delete(@PathParam("rollCode") String code) { service.archive(code); }
}
```

- [ ] **Step 4: ChildSlittingOrderResource + from-demand stub 501**

```java
@POST
@Path("/from-demand")
public Response fromDemand() {
    return Response.status(501).entity(Map.of("message", "Not implemented in v1")).build();
}
```

- [ ] **Step 5: QuarkusTest**

Create: `src/test/java/com/plantops/api/SlittingMasterRollResourceTest.java`

```java
@QuarkusTest
class SlittingMasterRollResourceTest {
    @Test
    void createAndListMasterRoll() {
        String body = """
            {"rollCode":"MR-DEMO-01","widthMm":1200,"lengthMm":5000,
             "kerfLongitudinalMm":2,"kerfTransverseMm":2,"status":"AVAILABLE"}
            """;
        given().contentType(ContentType.JSON).body(body)
            .when().post("/api/v1/slitting/master-rolls")
            .then().statusCode(200)
            .body("rollCode", equalTo("MR-DEMO-01"));

        given().when().get("/api/v1/slitting/master-rolls")
            .then().statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }
}
```

Run: `./mvnw -q test -Dtest=SlittingMasterRollResourceTest`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

---

### Task 4: Demo 种子数据

**Files:**
- Create: `src/main/resources/db/migration/V49_1__slitting_nest_demo.sql`

- [ ] **Step 1: 插入 demo 数据（workspace_id = 'default' 或与 sample 一致）**

```sql
INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, kerf_longitudinal_mm, kerf_transverse_mm, status)
VALUES ('default', 'MR-1200-5000-A', 1200, 5000, 2, 2, 'AVAILABLE'),
       ('default', 'MR-1200-5000-B', 1200, 5000, 2, 2, 'AVAILABLE');

INSERT INTO intermediate_roll_catalog (workspace_id, spec_code, width_mm, length_mm, cutting_method, kerf_mm)
VALUES ('default', 'INT-600-2500', 600, 2500, 'LONGITUDINAL', 2),
       ('default', 'INT-400-2500', 400, 2500, 'LONGITUDINAL', 2),
       ('default', 'INT-600-2000', 600, 2000, 'TRANSVERSE', 2);

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority)
VALUES ('default', 'CO-001', 280, 1200, 2, 10),
       ('default', 'CO-002', 320, 1100, 1, 9),
       ('default', 'CO-003', 150, 800, 3, 8),
       ('default', 'CO-004', 200, 900, 2, 7);
-- 继续至 8–12 条
```

- [ ] **Step 2: Commit**

---

## Sprint S2 — P1a：Timefold 几何 + 硬约束

### Task 5: 求解域基础类

**Files:**
- Create: `solver/slitting/RollType.java`, `CuttingMethod.java`, `Dimensions.java`
- Create: `solver/slitting/RollNode.java`
- Create: `solver/slitting/NestAssignment.java`
- Create: `solver/slitting/SlittingNestSolution.java`
- Create: `solver/slitting/SlittingProblemFacts.java`
- Create: `solver/slitting/SlittingGeometryUtil.java`

- [ ] **Step 1: SlittingGeometryUtil**

```java
public final class SlittingGeometryUtil {
    private SlittingGeometryUtil() {}

    public static double effectiveWidth(RollNode node, boolean rotated) {
        return rotated ? node.getDimensions().lengthMm() : node.getDimensions().widthMm();
    }

    public static double effectiveLength(RollNode node, boolean rotated) {
        return rotated ? node.getDimensions().widthMm() : node.getDimensions().lengthMm();
    }

    public static boolean overlaps(NestAssignment a, NestAssignment b) {
        if (a.getParentNode() == null || b.getParentNode() == null) return false;
        if (!a.getParentNode().getNodeId().equals(b.getParentNode().getNodeId())) return false;
        double ax2 = a.getPositionX() + effectiveWidth(a.getPlacedNode(), a.isRotated());
        double ay2 = a.getPositionY() + effectiveLength(a.getPlacedNode(), a.isRotated());
        double bx2 = b.getPositionX() + effectiveWidth(b.getPlacedNode(), b.isRotated());
        double by2 = b.getPositionY() + effectiveLength(b.getPlacedNode(), b.isRotated());
        return a.getPositionX() < bx2 && ax2 > b.getPositionX()
            && a.getPositionY() < by2 && ay2 > b.getPositionY();
    }
}
```

- [ ] **Step 2: NestAssignment 规划实体**

```java
@PlanningEntity
public class NestAssignment {
    @PlanningId
    private String assignmentId;
    private RollNode placedNode; // problem fact link

    @PlanningVariable(valueRangeProviderRefs = "containerRange")
    private RollNode parentNode;

    @PlanningVariable(valueRangeProviderRefs = "xRange")
    private Integer positionX;

    @PlanningVariable(valueRangeProviderRefs = "yRange")
    private Integer positionY;

    @PlanningVariable
    private Boolean rotated;

    private int sequence;
    // getters/setters
}
```

- [ ] **Step 3: SlittingNestSolution**

```java
@PlanningSolution
public class SlittingNestSolution {
    @ProblemFactProperty
    private SlittingProblemFacts problemFacts;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "containerRange")
    private List<RollNode> containers;

    @PlanningEntityCollectionProperty
    private List<NestAssignment> assignments;

    @PlanningScore
    private HardSoftScore score;

    public HardSoftScore getScore() { return score; }
    public void setScore(HardSoftScore score) { this.score = score; }
}
```

- [ ] **Step 4: Commit**

---

### Task 6: SlittingConstraintProvider 硬约束 + 测试

**Files:**
- Create: `src/main/java/com/plantops/solver/slitting/SlittingConstraintProvider.java`
- Create: `src/test/java/com/plantops/solver/slitting/SlittingConstraintProviderTest.java`

- [ ] **Step 1: 写失败测试 — boundaryOverflow**

```java
@Test
void boundaryOverflow_penalizesWhenChildExceedsParent() {
    RollNode parent = master("P1", 1000, 2000);
    RollNode child = child("C1", 400, 500);
    NestAssignment a = assignment("A1", child, parent, 700, 0, false);

    HardSoftScore score = score(new SlittingNestSolution(
        facts(), List.of(parent), List.of(a)));

    assertTrue(score.hardScore() < 0);
}
```

- [ ] **Step 2: Run 确认 FAIL**

Run: `./mvnw -q test -Dtest=SlittingConstraintProviderTest#boundaryOverflow_penalizesWhenChildExceedsParent`
Expected: FAIL（ConstraintProvider 未实现）

- [ ] **Step 3: 实现硬约束**

```java
private Constraint boundaryOverflow(ConstraintFactory factory) {
    return factory.forEach(NestAssignment.class)
        .filter(a -> a.getParentNode() != null && a.getPlacedNode() != null)
        .filter(a -> {
            double w = SlittingGeometryUtil.effectiveWidth(a.getPlacedNode(), Boolean.TRUE.equals(a.getRotated()));
            double h = SlittingGeometryUtil.effectiveLength(a.getPlacedNode(), Boolean.TRUE.equals(a.getRotated()));
            return a.getPositionX() + w > a.getParentNode().getDimensions().widthMm()
                || a.getPositionY() + h > a.getParentNode().getDimensions().lengthMm()
                || a.getPositionX() < 0 || a.getPositionY() < 0;
        })
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("Boundary overflow");
}

private Constraint noOverlap(ConstraintFactory factory) {
    return factory.forEachUniquePair(NestAssignment.class,
            Joiners.equal(NestAssignment::getParentNode))
        .filter(SlittingGeometryUtil::overlaps)
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("No overlap");
}
```

- [ ] **Step 4: 补全 noOverlap、childParentType 测试并通过**

Run: `./mvnw -q test -Dtest=SlittingConstraintProviderTest`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

---

### Task 7: SolverRuntimeFactory + 单阶段求解 smoke test

**Files:**
- Modify: `src/main/java/com/plantops/config/SolverRuntimeFactory.java`
- Modify: `src/main/java/com/plantops/config/ParameterRegistry.java`
- Create: `src/test/java/com/plantops/scenario/slitting/SlittingSinglePhaseSolverTest.java`

- [ ] **Step 1: ParameterRegistry 增加默认参数**

```java
defaults.put("slitting_solver_seconds", "30");
```

- [ ] **Step 2: SolverRuntimeFactory 增加方法**

```java
public SolverManager<SlittingNestSolution> createSlittingNestSolver() {
    SolverConfig config = new SolverConfig()
        .withSolutionClass(SlittingNestSolution.class)
        .withEntityClasses(NestAssignment.class)
        .withConstraintProviderClass(SlittingConstraintProvider.class);
    long seconds = Math.max(1L, parameters.getInt("slitting_solver_seconds", 30));
    config.withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(seconds));
    return SolverManager.create(SolverFactory.create(config));
}
```

- [ ] **Step 3: 单 MASTER + 多 CHILD smoke test（硬分 = 0）**

用 demo 尺寸构造 1 母卷 + 3 child，运行 solver，断言 `score.hardScore() == 0`。

Run: `./mvnw -q test -Dtest=SlittingSinglePhaseSolverTest`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

---

## Sprint S3 — P1b：方案 Service + 持久化 + solve API

### Task 8: SlittingPlanningContext + ProblemMapper

**Files:**
- Create: `scenario/slitting/SlittingPlanningContext.java`
- Create: `scenario/slitting/SlittingPlanningContextBuilder.java`
- Create: `scenario/slitting/SlittingProblemMapper.java`

- [ ] **Step 1: SlittingPlanningContext record**

```java
public record SlittingPlanningContext(
    String planVersionId,
    List<MasterRollEntity> masterRolls,
    List<ChildSlittingOrderEntity> childOrders,
    List<IntermediateRollCatalogEntity> catalog,
    List<SlittingRollNodeEntity> existingNodes,
    List<SlittingAssignmentEntity> existingAssignments) {}
```

- [ ] **Step 2: Builder 从 plan_version 关联表加载**

- [ ] **Step 3: ProblemMapper.toPhase1Solution — 按 quantity 展开 CHILD RollNode**

```java
public SlittingNestSolution toPhase1Solution(SlittingPlanningContext ctx) {
    List<RollNode> containers = ctx.catalog().stream()
        .filter(c -> c.active)
        .map(this::toIntermediateContainer)
        .toList();
    List<NestAssignment> assignments = expandChildAssignments(ctx.childOrders());
    return new SlittingNestSolution(facts(ctx), containers, assignments);
}
```

- [ ] **Step 4: Commit**

---

### Task 9: SlittingPlanService + REST

**Files:**
- Create: `scenario/slitting/SlittingPlanService.java`
- Create: `scenario/slitting/SlittingUtilizationCalculator.java`
- Create: `api/SlittingPlanResource.java`
- Create: `api/dto/slitting/SlittingPlanDtos.java`
- Create: `src/test/java/com/plantops/api/SlittingPlanResourceTest.java`

- [ ] **Step 1: createPlan**

```java
@Transactional
public SlittingPlanSummaryDto createPlan(CreateSlittingPlanRequest req) {
    String planVersionId = "SLIT-" + UUID.randomUUID().toString().substring(0, 8);
    SlittingPlanVersionEntity plan = new SlittingPlanVersionEntity();
    plan.stampWorkspace();
    plan.planVersionId = planVersionId;
    plan.name = req.name();
    plan.status = "DRAFT";
    plan.persist();
    // link master rolls + child orders by code
    return toSummary(plan);
}
```

- [ ] **Step 2: solvePlan（首版可先单阶段 Phase2 仅 MASTER+CHILD，见 Task 10 前临时路径）**

- [ ] **Step 3: persistResult — 写 slitting_roll_node + slitting_assignment**

- [ ] **Step 4: GET /tree 返回 SlittingPlanTreeDto**

- [ ] **Step 5: 集成测试 create → solve → get tree**

Run: `./mvnw -q test -Dtest=SlittingPlanResourceTest`
Expected: BUILD SUCCESS，`utilizationPct > 0`

- [ ] **Step 6: Commit**

---

## Sprint S4 — P3：两阶段 Pipeline + 软约束

### Task 10: SlittingLayeredSolverPipeline

**Files:**
- Create: `scenario/slitting/SlittingLayeredSolverPipeline.java`
- Create: `scenario/slitting/SlittingPhaseResult.java`
- Create: `solver/slitting/SlittingConstructionHeuristic.java`
- Modify: `scenario/slitting/SlittingPlanService.java`
- Create: `src/test/java/com/plantops/scenario/slitting/SlittingLayeredSolverPipelineTest.java`

- [ ] **Step 1: Phase1 求解并 materialize INTERMEDIATE 节点**

```java
@ApplicationScoped
public class SlittingLayeredSolverPipeline {
    @Inject SolverRuntimeFactory solverRuntimeFactory;
    @Inject SlittingProblemMapper mapper;

    public SlittingLayeredResult solve(SlittingPlanningContext ctx) throws ExecutionException, InterruptedException {
        SlittingNestSolution phase1Problem = mapper.toPhase1Solution(ctx);
        SlittingConstructionHeuristic.seedFFD(phase1Problem);
        SlittingNestSolution phase1Solved = solve(phase1Problem);

        List<RollNode> intermediates = mapper.materializeIntermediates(phase1Solved);
        SlittingNestSolution phase2Problem = mapper.toPhase2Solution(ctx.masterRolls(), intermediates);
        SlittingConstructionHeuristic.seedFFD(phase2Problem);
        SlittingNestSolution phase2Solved = solve(phase2Problem);

        return new SlittingLayeredResult(phase1Solved, phase2Solved, mergeTree(phase1Solved, phase2Solved));
    }
}
```

- [ ] **Step 2: 软约束 wasteAreaByDepth + nonStandardIntermediatePenalty**

在 `SlittingConstraintProvider` 追加；测试软分随废料增加而变差。

- [ ] **Step 3: SlittingPlanService.solvePlan 改调 Pipeline**

持久化三层树：MASTER（来自 master_roll）→ INTERMEDIATE（Phase1 输出）→ CHILD。

- [ ] **Step 4: Pipeline 集成测试**

Run: `./mvnw -q test -Dtest=SlittingLayeredSolverPipelineTest`
Expected: hardScore=0，节点类型层级正确

- [ ] **Step 5: Commit**

---

## Sprint S5 — P2a：前端基础 + SAT + KPI

### Task 11: 前端依赖与类型

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/src/types/slitting.ts`
- Create: `frontend/src/api/slittingClient.ts`

- [ ] **Step 1: 安装依赖**

Run: `cd frontend && npm install konva react-konva zustand`

- [ ] **Step 2: slitting.ts 类型**

```typescript
export type RollNodeType = 'MASTER' | 'INTERMEDIATE' | 'CHILD';

export interface SlittingRollNodeDto {
  nodeId: string;
  nodeType: RollNodeType;
  parentNodeId: string | null;
  widthMm: number;
  lengthMm: number;
  cuttingMethod?: string;
}

export interface SlittingAssignmentDto {
  assignmentId: string;
  childNodeId: string;
  parentNodeId: string;
  posXMm: number;
  posYMm: number;
  rotated: boolean;
  sequence?: number;
}

export interface SlittingPlanTreeDto {
  planVersionId: string;
  nodes: SlittingRollNodeDto[];
  assignments: SlittingAssignmentDto[];
  utilizationPct?: number;
}
```

- [ ] **Step 3: slittingClient.ts 封装 fetch**

Base: `/api/v1/slitting`

- [ ] **Step 4: Commit**

---

### Task 12: SAT + KPI 纯函数测试

**Files:**
- Create: `frontend/src/utils/slitting/satCollision.ts`
- Create: `frontend/src/utils/slitting/kpi.ts`
- Create: `frontend/src/utils/slitting/satCollision.test.ts`
- Create: `frontend/src/utils/slitting/kpi.test.ts`

- [ ] **Step 1: satCollision.test.ts 失败测试**

```typescript
import { describe, expect, it } from 'vitest';
import { aabbOverlap, effectiveSize } from './satCollision';

describe('aabbOverlap', () => {
  it('detects overlap', () => {
    const a = { x: 0, y: 0, w: 100, h: 100 };
    const b = { x: 50, y: 50, w: 100, h: 100 };
    expect(aabbOverlap(a, b)).toBe(true);
  });
});
```

- [ ] **Step 2: 实现 aabbOverlap + effectiveSize（含 rotated 交换 w/h）**

- [ ] **Step 3: kpi.ts**

```typescript
export function computeUtilizationPct(
  masterNodes: { widthMm: number; lengthMm: number }[],
  childAssignments: { childNodeId: string; rotated: boolean }[],
  nodeById: Map<string, { widthMm: number; lengthMm: number }>,
): number {
  const masterArea = masterNodes.reduce((s, n) => s + n.widthMm * n.lengthMm, 0);
  if (masterArea <= 0) return 0;
  const placedArea = childAssignments.reduce((s, a) => {
    const n = nodeById.get(a.childNodeId);
    if (!n) return s;
    const w = a.rotated ? n.lengthMm : n.widthMm;
    const h = a.rotated ? n.widthMm : n.lengthMm;
    return s + w * h;
  }, 0);
  return (placedArea / masterArea) * 100;
}
```

- [ ] **Step 4: Run tests**

Run: `cd frontend && npm run test -- src/utils/slitting`
Expected: PASS

- [ ] **Step 5: Commit**

---

### Task 13: Zustand store + 画板骨架

**Files:**
- Create: `frontend/src/store/slitting/workbenchStore.ts`
- Create: `frontend/src/components/slitting/SlittingCanvas.tsx`
- Create: `frontend/src/components/slitting/SlittingToolbar.tsx`
- Create: `frontend/src/components/slitting/ChildOrderPool.tsx`
- Create: `frontend/src/pages/slitting/SlittingWorkbenchPage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/Layout.tsx`

- [ ] **Step 1: workbenchStore**

```typescript
import { create } from 'zustand';
import type { SlittingAssignmentDto, SlittingRollNodeDto } from '../../types/slitting';

interface WorkbenchState {
  planVersionId: string | null;
  nodes: SlittingRollNodeDto[];
  assignments: SlittingAssignmentDto[];
  activeParentNodeId: string | null;
  utilizationPct: number;
  setTree: (planVersionId: string, nodes: SlittingRollNodeDto[], assignments: SlittingAssignmentDto[]) => void;
  setActiveParent: (nodeId: string | null) => void;
  moveAssignment: (assignmentId: string, x: number, y: number, rotated?: boolean) => void;
  recalcKpi: () => void;
}

export const useSlittingWorkbenchStore = create<WorkbenchState>((set, get) => ({
  planVersionId: null,
  nodes: [],
  assignments: [],
  activeParentNodeId: null,
  utilizationPct: 0,
  setTree: (planVersionId, nodes, assignments) => set({ planVersionId, nodes, assignments, activeParentNodeId: null }),
  setActiveParent: (nodeId) => set({ activeParentNodeId: nodeId }),
  moveAssignment: (assignmentId, x, y, rotated) => {
    const next = get().assignments.map(a =>
      a.assignmentId === assignmentId ? { ...a, posXMm: x, posYMm: y, rotated: rotated ?? a.rotated } : a,
    );
    set({ assignments: next });
    get().recalcKpi();
  },
  recalcKpi: () => { /* call computeUtilizationPct */ },
}));
```

- [ ] **Step 2: SlittingCanvas — Stage + 母卷 Rect + assignment Rect**

缩放：`scale = Math.min(viewportW / parent.widthMm, viewportH / parent.lengthMm) * 0.9`

- [ ] **Step 3: 拖放 onDragEnd — SAT 校验，碰撞则回弹并标红**

- [ ] **Step 4: 路由与导航**

`App.tsx` 增加：
```tsx
<Route path="slitting/master-data" element={<SlittingMasterDataPage />} />
<Route path="slitting/workbench" element={<SlittingWorkbenchPage />} />
<Route path="slitting/plans" element={<SlittingPlansPage />} />
```

`Layout.tsx` 增加 SLITTING_GROUP。

- [ ] **Step 5: Build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

---

## Sprint S6 — P2b：多层钻取 + 闭环

### Task 14: 主数据页 + 方案列表页

**Files:**
- Create: `frontend/src/pages/slitting/SlittingMasterDataPage.tsx`
- Create: `frontend/src/pages/slitting/SlittingPlansPage.tsx`

- [ ] **Step 1: MasterData 三 Tab（母卷 / 中间卷规格 / 子订单）复用 FilterableTable 模式**

- [ ] **Step 2: Plans 列表 — 创建方案对话框选 masterRollCodes + childOrderCodes**

- [ ] **Step 3: Workbench 顶栏增加方案选择器**

- [ ] **Step 4: Commit**

---

### Task 15: 树面板 + 多层钻取

**Files:**
- Create: `frontend/src/components/slitting/RollTreePanel.tsx`
- Modify: `frontend/src/components/slitting/SlittingCanvas.tsx`

- [ ] **Step 1: RollTreePanel — 递归渲染 node tree，点击 INTERMEDIATE 调 setActiveParent**

- [ ] **Step 2: Canvas 根据 activeParentNodeId 过滤 assignments**

- `null` → 显示 parent 为 MASTER 的 assignments（INTERMEDIATE 块）
- 非 null → 显示 parent 为 active INTERMEDIATE 的 CHILD assignments

- [ ] **Step 3: 双向高亮（hover nodeId）**

- [ ] **Step 4: Commit**

---

### Task 16: 求解 / 保存闭环 + 端到端验收

**Files:**
- Modify: `frontend/src/pages/slitting/SlittingWorkbenchPage.tsx`
- Modify: `frontend/src/api/slittingClient.ts`

- [ ] **Step 1: 工具栏按钮**

```typescript
async function handleSolve() {
  const id = useSlittingWorkbenchStore.getState().planVersionId;
  if (!id) return;
  const result = await slittingClient.solvePlan(id);
  const tree = await slittingClient.getPlanTree(id);
  useSlittingWorkbenchStore.getState().setTree(id, tree.nodes, tree.assignments);
}

async function handleSave() {
  const { planVersionId, assignments } = useSlittingWorkbenchStore.getState();
  if (!planVersionId) return;
  await slittingClient.saveAssignments(planVersionId, assignments);
}
```

- [ ] **Step 2: 后端 PUT assignments 校验 SAT + boundary（可选调用 SlittingGeometryUtil）**

- [ ] **Step 3: 全量验证**

Run:
```bash
./mvnw test
cd frontend && npm run test && npm run build
```
Expected: 全部 PASS

- [ ] **Step 4: 手工验收清单**

1. 打开 `/slitting/master-data`，可见 demo 母卷与子订单  
2. `/slitting/plans` 创建方案并进入 workbench  
3. 点击「求解」，画板出现 INTERMEDIATE + CHILD 布局，KPI > 0  
4. 拖拽 CHILD 块，碰撞时红框且松手回弹  
5. 点击 INTERMEDIATE 钻取 CHILD 层  
6. 「保存」后刷新页面布局保持  

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(slitting): complete v1 workbench solve/save loop"
```

---

## Spec Coverage Checklist

| Spec 要求 | 对应 Task |
|-----------|-----------|
| V49 表结构 + APS 可空外键 | Task 1–2 |
| CRUD API `/api/v1/slitting/*` | Task 3 |
| Demo 数据 | Task 4 |
| NestAssignment + 硬约束 | Task 5–6 |
| SolverRuntimeFactory | Task 7 |
| createPlan / solve / tree / assignments | Task 8–9 |
| 两阶段 Pipeline + 软约束 | Task 10 |
| Konva + Zustand + SAT + KPI | Task 11–13 |
| 路由 + 导航 | Task 13–14 |
| 多层钻取 + 闭环 | Task 15–16 |
| from-demand 501 stub | Task 3 |
| P4 Session | 不在本计划（spec 后续迭代） |

---

## 执行方式

**Plan complete and saved to `docs/superpowers/plans/2026-06-04-slitting-nest.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — 每个 Task 派发独立 subagent，Task 间做 review，适合 S1–S6 长链路。

**2. Inline Execution** — 在本会话按 Task 顺序执行，每完成一个 Sprint 设 checkpoint 与你确认。

**Which approach?**
