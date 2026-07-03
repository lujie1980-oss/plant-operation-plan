# OTD 主计划本体 M3 — PeriodSequence 混合桶 + SRP 进 Session + Operation 时间窗 + Sandbox 统一

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 M2（供需聚合 + Timefold 桥接 + confirm）基础上：周期模型升级为可配置的日→周→月混合桶（PeriodSequence）；SRP 进入 Session 装载并由 optimize 回写 `reservedCapacity`；为 SupplyOrder 建 Operation 链并 derive `earliest/latest_possible` 时间窗；抽取统一 `OntologySandbox` 基础设施合并 S05；对「本体直驱求解」完成评估并记录决策。

**Architecture:** 周期映射从「日差除法」收敛到 `PeriodIndex`（按 Period 日期区间查找），消除 `OntologyLoader` / `OntologyTimefoldMapper` 的重复实现。SRP 容量来自 `ResourceCalendarEntity` 按 period 聚合；optimize 的 allocation 按 `(resourceId, period)` 聚合 `durationMinutes` 回写 SRP。Operation 链按 `ProductResourceEntity.sequenceNo` 建步序，时间窗用 JIT 倒推 + 正排两条 derived 链。Sandbox 合并只抽公共 Store 基础设施，不动现有 REST API。

**Tech Stack:** Java 21, Quarkus, Timefold 3.x, JUnit 5；React + 现有 `frontend/src`。

**Locked decisions (继承 M1/M2 + M3 新增):**

| # | 决策 | 值 |
|---|------|-----|
| D1–D8 | 见 M2 计划 | 不变（D5 复用求解策略 M3 维持，见 D16） |
| D9 | PeriodSequence 配置 | `SystemParameterEntity` 参数 `ontology_period_sequence`，格式 `"14x1d,4x1w,2x1m"`；缺省回退 `28x1d`（兼容 M2 行为与现有测试） |
| D10 | date→period 映射 | 新 `PeriodIndex`（基于 `periodsOrdered` 日期区间；早于首桶→0，晚于末桶→last），替换两处 `periodIndexForDate` 重复实现 |
| D11 | SRP 装载 | 资源集合 = `ProductionLineEntity.resourceId` 去重；`totalCapacity` = Σ `ResourceCalendarEntity.availableCapacityMinutes`（calendarDate ∈ period）；`calendarDowntime` = Σ `unavailableCapacityMinutes`；`technicalDowntime` = 0 |
| D12 | reservedCapacity 回写 | optimize 时 Σ `allocation.durationMinutes` per `(resourceId, period)` → ChangeSet `(SRP, "reservedCapacity", abs)` |
| D13 | Operation 范围 | 每 `SupplyOrder` × 该 productCode 的工序（`ProductResourceEntity` 按 `operationName` 去重、`sequenceNo` 排序）生成 Operation；`productionTimeMinutes = setupTimeMinutes + processTimeSeconds × quantity / 60` |
| D14 | 时间窗语义（M3 简化） | `latestPossibleEnd(last) = supplyOrder.needDate`，向前 JIT 倒推；`earliestPossibleStart(first) = planningStart`（暂无上游物料 Fulfillment），向后正排；`earliest > latest` → `infeasible = true` |
| D15 | RolEngine 规则合并 | 新 `withMasterPlanRules(graph)` = PISPP + SRP + Operation 三组 derivation 合一 registry；Session 创建改用它 |
| D16 | 本体直驱求解 | M3 仅评估（spike 文档 + 决策记录入 mapping 文档），不实施；D5 复用策略不变 |
| D17 | Sandbox 合并方式 | 抽 `OntologySandbox` 接口 + 泛型 `OntologySandboxStore<S>`；`SchedulingSessionStore` / `MasterPlanOntologySessionStore` 改为继承；REST API 与 DTO 不变 |

**M3 验收（约 4–6 周）：**

1. `ontology_period_sequence=14x1d,4x1w,2x1m` 时 Session periodCount=20，混合桶日期区间正确；缺省参数行为与 M2 一致（28 日桶，全量回归通过）
2. Session 创建后 `graph.srpById()` 非空；`GET /api/v1/master-plan/sessions/{id}/resources` 返回 SRP 快照
3. optimize 后对应 `(resourceId, period)` 的 SRP `reservedCapacity > 0` 且 `freeCapacity` 联动重算
4. 每个含工序的 SupplyOrder 拥有 Operation 链，时间窗满足 `latest(i) = latest(i+1) − prodTime(i+1)`；修改 needDate 后窗口经 ROL 传播重算
5. `SchedulingSessionStore` / `MasterPlanOntologySessionStore` 复用同一 Sandbox 基础设施，现有 S05 与 M2 测试零回归
6. `docs/otd-ontology-direct-solve-evaluation.md` 完成直驱求解评估结论

**Related:** [M1 计划](./2026-06-07-otd-ontology-master-plan.md)、[M2 计划](./2026-06-09-otd-ontology-master-plan-m2.md)、[otd-ontology-mapping.md](../../otd-ontology-mapping.md)

---

## File structure (M3)

```
src/main/java/com/plantops/
  ontology/
    OntologyIds.java                    (modify: srpId/operationId 工厂方法)
    OntologyLoader.java                 (modify: PeriodSequence 展开、SRP 装载、Operation 装载；periodIndexForDate → PeriodIndex)
    OntologyGraph.java                  (modify: operationsById 索引 + builder)
    period/
      PeriodSequenceSpec.java           (new: "14x1d,4x1w,2x1m" 解析 + expand)
      PeriodIndex.java                  (new: date → period sequenceNr 查找)
    supply/
      Operation.java                    (new: 步序 + 时间窗字段)
  rol/
    OperationTimeWindowDerivations.java (new: earliest/latest 链规则)
    RolEngine.java                      (modify: withMasterPlanRules + SupplyOrder needDate 入口 + Operation 属性入口)
    DerivationRegistry.java             (modify: 支持合并构造，若已支持则跳过)
    PispPeriodDerivations.java          (modify: 暴露 derivations(graph) 列表方法)
    SrpCapacityDerivations.java         (modify: 同上)
  scenario/planning/
    MasterPlanOntologySessionService.java (modify: withMasterPlanRules、resources/operations 查询、optimize 回写 SRP)
    OntologyTimefoldMapper.java         (modify: 用 PeriodIndex；新增 SRP reserved 操作)
    sandbox/
      OntologySandbox.java              (new: 接口)
      OntologySandboxStore.java         (new: 泛型 store 基类)
    SchedulingSessionStore.java         (modify: 继承 OntologySandboxStore)
    MasterPlanOntologySessionStore.java (modify: 继承 OntologySandboxStore)
    SchedulingSession.java              (modify: implements OntologySandbox)
    MasterPlanOntologySession.java      (modify: implements OntologySandbox)
  api/
    MasterPlanSessionResource.java      (modify: GET resources / GET supply-orders/{id}/operations)
    dto/planning/
      SrpSnapshotDto.java               (new)
      OperationSnapshotDto.java         (new)

frontend/src/
  types/ontology.ts                     (modify: SrpSnapshotDto / OperationSnapshotDto)
  api/client.ts                         (modify: listResources / listOperations)
  pages/MasterPlanOntologyPage.tsx      (modify: 资源产能表 tab)
  components/SrpCapacityTable.tsx       (new)

src/test/java/com/plantops/
  ontology/period/PeriodSequenceSpecTest.java        (new)
  ontology/period/PeriodIndexTest.java               (new)
  ontology/OntologyLoaderSrpTest.java                (new)
  ontology/OntologyLoaderOperationTest.java          (new)
  rol/OperationTimeWindowDerivationTest.java         (new)
  scenario/planning/MasterPlanOntologySessionSrpTest.java (new)
  scenario/planning/OntologySandboxStoreTest.java    (new)

docs/
  otd-ontology-direct-solve-evaluation.md (new: Epic E 产物)
```

---

## Epic A: PeriodSequence 混合桶（前置，其余 Epic 依赖）

### Task A.1: PeriodSequenceSpec — 解析与展开

**Files:**
- Create: `src/main/java/com/plantops/ontology/period/PeriodSequenceSpec.java`
- Test: `src/test/java/com/plantops/ontology/period/PeriodSequenceSpecTest.java`

- [ ] **Step 1: Write failing test**

```java
class PeriodSequenceSpecTest {

    @Test
    void parsesMixedSpecAndExpandsPeriods() {
        PeriodSequenceSpec spec = PeriodSequenceSpec.parse("2x1d,1x1w,1x1m");
        List<Period> periods = spec.expand(LocalDate.of(2026, 6, 1));
        assertEquals(4, periods.size());
        // 2 daily
        assertEquals(LocalDate.of(2026, 6, 1), periods.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 1), periods.get(0).getEndDate());
        assertEquals(LocalDate.of(2026, 6, 2), periods.get(1).getStartDate());
        // 1 weekly: 6/3 – 6/9
        assertEquals(LocalDate.of(2026, 6, 3), periods.get(2).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 9), periods.get(2).getEndDate());
        // 1 monthly(30d): 6/10 – 7/9
        assertEquals(LocalDate.of(2026, 6, 10), periods.get(3).getStartDate());
        assertEquals(LocalDate.of(2026, 7, 9), periods.get(3).getEndDate());
        // sequenceNr 连续
        assertEquals(3, periods.get(3).getSequenceNr());
    }

    @Test
    void defaultSpecIs28Daily() {
        List<Period> periods = PeriodSequenceSpec.defaultSpec().expand(LocalDate.of(2026, 6, 1));
        assertEquals(28, periods.size());
        assertEquals(periods.get(5).getStartDate(), periods.get(5).getEndDate());
    }

    @Test
    void invalidSpecFallsBackToDefault() {
        assertEquals(28, PeriodSequenceSpec.parseOrDefault("garbage").expand(LocalDate.now()).size());
        assertEquals(28, PeriodSequenceSpec.parseOrDefault(null).expand(LocalDate.now()).size());
    }
}
```

- [ ] **Step 2: Run — FAIL**

Run (PowerShell): `cd d:\AILab\PlantOperationPlan\plant-operation-plan; .\mvnw.cmd -q test "-Dtest=PeriodSequenceSpecTest"`

- [ ] **Step 3: Implement**

```java
public final class PeriodSequenceSpec {

    /** 段：count 个长度为 lengthDays 的桶。 */
    public record Segment(int count, int lengthDays) {}

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)x(\\d+)([dwm])");

    private final List<Segment> segments;

    private PeriodSequenceSpec(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    public static PeriodSequenceSpec defaultSpec() {
        return new PeriodSequenceSpec(List.of(new Segment(OntologyIds.DEFAULT_PERIOD_COUNT, 1)));
    }

    /** "14x1d,4x1w,2x1m" → segments；d=1天 w=7天 m=30天。 */
    public static PeriodSequenceSpec parse(String text) {
        List<Segment> segments = new ArrayList<>();
        for (String token : text.split(",")) {
            Matcher m = SEGMENT.matcher(token.trim().toLowerCase());
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid period segment: " + token);
            }
            int unitDays = switch (m.group(3)) {
                case "d" -> 1;
                case "w" -> 7;
                case "m" -> 30;
                default -> throw new IllegalArgumentException(token);
            };
            segments.add(new Segment(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) * unitDays));
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Empty spec");
        }
        return new PeriodSequenceSpec(segments);
    }

    public static PeriodSequenceSpec parseOrDefault(String text) {
        if (text == null || text.isBlank()) {
            return defaultSpec();
        }
        try {
            return parse(text);
        } catch (IllegalArgumentException ex) {
            return defaultSpec();
        }
    }

    public List<Period> expand(LocalDate planningStart) {
        List<Period> periods = new ArrayList<>();
        LocalDate cursor = planningStart;
        int seq = 0;
        for (Segment segment : segments) {
            for (int i = 0; i < segment.count(); i++) {
                LocalDate end = cursor.plusDays(segment.lengthDays() - 1L);
                periods.add(new Period(OntologyIds.periodId(seq), seq, cursor, end));
                cursor = end.plusDays(1);
                seq++;
            }
        }
        return periods;
    }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit** — `feat(ontology): add PeriodSequenceSpec for mixed day/week/month buckets`

### Task A.2: PeriodIndex — 统一 date→period 映射

**Files:**
- Create: `src/main/java/com/plantops/ontology/period/PeriodIndex.java`
- Test: `src/test/java/com/plantops/ontology/period/PeriodIndexTest.java`

- [ ] **Step 1: Write failing test**

```java
class PeriodIndexTest {

    @Test
    void mapsDateIntoOwningBucketAndClampsEdges() {
        List<Period> periods = PeriodSequenceSpec.parse("2x1d,1x1w").expand(LocalDate.of(2026, 6, 1));
        PeriodIndex index = PeriodIndex.of(periods);
        assertEquals(0, index.sequenceFor(LocalDate.of(2026, 6, 1)));
        assertEquals(1, index.sequenceFor(LocalDate.of(2026, 6, 2)));
        assertEquals(2, index.sequenceFor(LocalDate.of(2026, 6, 5)));   // 周桶中段
        assertEquals(0, index.sequenceFor(LocalDate.of(2026, 5, 20)));  // 早于首桶 → 0
        assertEquals(2, index.sequenceFor(LocalDate.of(2026, 12, 31))); // 晚于末桶 → last
        assertEquals(0, index.sequenceFor(null));                       // null → 0
    }
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

```java
public final class PeriodIndex {

    private final List<Period> periodsOrdered;

    private PeriodIndex(List<Period> periodsOrdered) {
        this.periodsOrdered = periodsOrdered;
    }

    public static PeriodIndex of(List<Period> periodsOrdered) {
        return new PeriodIndex(List.copyOf(periodsOrdered));
    }

    public int sequenceFor(LocalDate date) {
        if (date == null || periodsOrdered.isEmpty()) {
            return 0;
        }
        if (date.isBefore(periodsOrdered.get(0).getStartDate())) {
            return 0;
        }
        for (Period period : periodsOrdered) {
            if (!date.isBefore(period.getStartDate()) && !date.isAfter(period.getEndDate())) {
                return period.getSequenceNr();
            }
        }
        return periodsOrdered.get(periodsOrdered.size() - 1).getSequenceNr();
    }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit** — `feat(ontology): add PeriodIndex date-to-bucket lookup`

### Task A.3: Loader/Mapper 切换到 Spec + PeriodIndex

**Files:**
- Modify: `src/main/java/com/plantops/ontology/OntologyLoader.java`
- Modify: `src/main/java/com/plantops/scenario/planning/OntologyTimefoldMapper.java`
- Test: 既有 `OntologyLoaderSupplyDemandTest` + 新增混合桶用例

- [ ] **Step 1: Write failing @QuarkusTest 用例**（加入 `OntologyLoaderSupplyDemandTest`）

```java
@Test
@TestTransaction
void mixedBucketSpecChangesPeriodCount() {
    setSystemParameter("ontology_period_sequence", "2x1d,1x1w"); // 测试辅助：写 SystemParameterEntity
    OntologyGraph g = loader.loadForWorkspace(LocalDate.now());
    assertEquals(3, g.periodsOrdered().size());
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

`OntologyLoader.buildPeriods` 改为：

```java
private static List<Period> buildPeriods(LocalDate planningStart) {
    String specText = SystemParameterEntity.findValueInWorkspace("ontology_period_sequence"); // 若无此查询方法则按现有参数读取模式实现
    return PeriodSequenceSpec.parseOrDefault(specText).expand(planningStart);
}
```

- `aggregateSupplyIntoPispp` / `aggregateSalesDemandIntoPispp` / `periodIndexForDate` 调用处改用 `PeriodIndex.of(periods).sequenceFor(date)`（构建一次，方法间传递）
- 删除 `OntologyLoader.periodIndexForDate` 与 `OntologyTimefoldMapper.periodIndexForDate`；Mapper 的 `toChangeSet` 签名追加 `PeriodIndex`（由 SessionService 从 `graph.periodsOrdered()` 构建后传入），`OntologyIds.DEFAULT_PERIOD_COUNT` 仅保留给 `defaultSpec`

- [ ] **Step 4: Run — PASS** `.\mvnw.cmd -q test "-Dtest=OntologyLoaderSupplyDemandTest,OntologyTimefoldMapperTest,MasterPlanOntologySessionServiceTest"`

- [ ] **Step 5: Commit** — `feat(ontology): drive period buckets from configurable PeriodSequence spec`

---

## Epic B: SRP 进 Session + optimize 回写 reservedCapacity

### Task B.1: Loader 装载 SRP

**Files:**
- Modify: `src/main/java/com/plantops/ontology/OntologyIds.java`（加 `srpId`）
- Modify: `src/main/java/com/plantops/ontology/OntologyLoader.java`
- Test: `src/test/java/com/plantops/ontology/OntologyLoaderSrpTest.java`

- [ ] **Step 1: Write failing @QuarkusTest** — 构造 1 条 `ProductionLineEntity(resourceId="RES-1")` + 2 条 `ResourceCalendarEntity`（同一 period 内 available=480/420, unavailable=0/60），断言：

```java
OntologyGraph g = loader.loadForWorkspace(planningStart);
StandardResourcePeriod srp = g.srp(OntologyIds.srpId("RES-1", 0));
assertEquals(960, srp.getTotalCapacity(), 1e-6);     // (480+0)+(420+60)：每行 available+unavailable 计入 total
assertEquals(60, srp.getCalendarDowntime(), 1e-6);
assertEquals(900, srp.getAvailableCapacity(), 1e-6); // total − downtime
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

`OntologyIds`：

```java
public static String srpId(String resourceId, int sequenceNr) {
    return "SRP-" + resourceId + "-" + periodId(sequenceNr);
}
```

`OntologyLoader.buildGraph` 在 PISPP 装载后新增：

```java
private static void loadStandardResourcePeriods(
        OntologyGraph.Builder builder, List<Period> periods, PeriodIndex periodIndex) {
    Set<String> resourceIds = new LinkedHashSet<>();
    for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
        if (line.resourceId != null && !line.resourceId.isBlank()) {
            resourceIds.add(line.resourceId);
        }
    }
    Map<String, StandardResourcePeriod> srpByKey = new LinkedHashMap<>();
    for (String resourceId : resourceIds) {
        for (Period period : periods) {
            StandardResourcePeriod srp = new StandardResourcePeriod(
                    OntologyIds.srpId(resourceId, period.getSequenceNr()), resourceId, period.getId());
            srpByKey.put(srp.getId(), srp);
            builder.standardResourcePeriod(srp);
        }
    }
    for (ResourceCalendarEntity cal : ResourceCalendarEntity.listInWorkspace()) {
        if (cal.resourceId == null || !resourceIds.contains(cal.resourceId) || cal.calendarDate == null) {
            continue;
        }
        int seq = periodIndex.sequenceFor(cal.calendarDate);
        StandardResourcePeriod srp = srpByKey.get(OntologyIds.srpId(cal.resourceId, seq));
        srp.setTotalCapacity(srp.getTotalCapacity() + cal.availableCapacityMinutes + cal.unavailableCapacityMinutes);
        srp.setCalendarDowntime(srp.getCalendarDowntime() + cal.unavailableCapacityMinutes);
    }
    srpByKey.values().forEach(StandardResourcePeriod::recalculateCapacityFields);
}
```

> 注意 totalCapacity 口径：日历行的 available+unavailable = 理论总产能；available 即 total−downtime，与 `recalculateCapacityFields` 公式自洽。

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit** — `feat(ontology): load StandardResourcePeriod capacity from resource calendar`

### Task B.2: 合并 derivation registry（withMasterPlanRules）

**Files:**
- Modify: `src/main/java/com/plantops/rol/PispPeriodDerivations.java`、`SrpCapacityDerivations.java`（各暴露 `static List<Derivation> derivations(OntologyGraph)`，`register` 改为包装）
- Modify: `src/main/java/com/plantops/rol/RolEngine.java`
- Modify: `src/main/java/com/plantops/scenario/planning/MasterPlanOntologySessionService.java`（create 改用 `withMasterPlanRules`）
- Test: `src/test/java/com/plantops/scenario/planning/MasterPlanOntologySessionSrpTest.java`

- [ ] **Step 1: Write failing test** — create session 后对某 SRP `applyPropertyChange(srp, "reservedCapacity", 120)`，断言 `freeCapacity` 联动重算且既有 PISPP simulate 用例不受影响

- [ ] **Step 2: Implement**

```java
public static RolEngine withMasterPlanRules(OntologyGraph graph) {
    List<Derivation> all = new ArrayList<>();
    all.addAll(PispPeriodDerivations.derivations(graph));
    all.addAll(SrpCapacityDerivations.derivations(graph));
    all.addAll(OperationTimeWindowDerivations.derivations(graph)); // Epic C 提供；C 未完成前先注释此行
    return new RolEngine(graph, new DerivationRegistry(all));
}
```

- [ ] **Step 3: Run — PASS**

- [ ] **Step 4: Commit** — `feat(rol): merge PISPP and SRP derivations into master plan rule set`

### Task B.3: optimize 回写 SRP + resources API + 前端表

**Files:**
- Modify: `src/main/java/com/plantops/rol/ChangeOperation.java`（加 `TARGET_STANDARD_RESOURCE_PERIOD` 常量）、`RolTransaction.java`（apply 支持 SRP 目标）
- Modify: `src/main/java/com/plantops/scenario/planning/OntologyTimefoldMapper.java`
- Create: `src/main/java/com/plantops/api/dto/planning/SrpSnapshotDto.java`
- Modify: `MasterPlanOntologySessionService`（`listResources`）、`MasterPlanSessionResource`（`GET /{sessionId}/resources`）
- Create: `frontend/src/components/SrpCapacityTable.tsx`；Modify: `types/ontology.ts`、`api/client.ts`、`MasterPlanOntologyPage.tsx`
- Test: `MasterPlanOntologySessionSrpTest`（加 optimize 用例）

- [ ] **Step 1: Write failing @QuarkusTest** — 造数：WO + allocation（`resourceId="RES-1"`, `slotDate∈P-0`, `durationMinutes=90`）；create → optimize → 断言 `g.srp(srpId("RES-1",0)).getReservedCapacity()==90` 且 `freeCapacity` 已减

- [ ] **Step 2: Implement Mapper 扩展**

`toChangeSet` 在 PISPP 聚合后追加：

```java
Map<String, Double> reservedBySrpId = new LinkedHashMap<>();
for (MasterPlanAllocationDto allocation : allocations) {
    if (allocation.resourceId() == null || allocation.resourceId().isBlank()) {
        continue;
    }
    int seq = periodIndex.sequenceFor(resolvePlannedDate(allocation));
    String srpId = OntologyIds.srpId(allocation.resourceId(), seq);
    if (graph.srp(srpId) == null) {
        continue;
    }
    reservedBySrpId.merge(srpId, (double) allocation.durationMinutes(), Double::sum);
}
for (Map.Entry<String, Double> entry : reservedBySrpId.entrySet()) {
    operations.add(new ChangeOperation(
            ChangeOperation.TARGET_STANDARD_RESOURCE_PERIOD,
            entry.getKey(), "reservedCapacity", entry.getValue()));
}
```

`RolTransaction.apply` 对 `TARGET_STANDARD_RESOURCE_PERIOD` 走 `rolEngine.applyPropertyChange(graph.srp(id), property, value)`。

- [ ] **Step 3: Implement API**

```java
public record SrpSnapshotDto(String id, String resourceId, String periodId,
        double totalCapacity, double calendarDowntime, double reservedCapacity,
        double availableCapacity, double freeCapacity, double overloadCapacity) {}
```

`listResources(sessionId)`：按 `resourceId`、period 序排序返回全部 SRP 快照。

- [ ] **Step 4: 前端** — `SrpCapacityTable`（列：资源/周期/总产能/停机/已占用/可用/空闲/超载，超载>0 标红）；`MasterPlanOntologyPage` 在明细表下方加「资源产能」区块，optimize 后刷新

- [ ] **Step 5: Run — PASS**；`cd frontend; npm run build`

- [ ] **Step 6: Commit** — `feat(ontology): write back reserved capacity to SRP on optimize with capacity view`

---

## Epic C: Operation 时间窗 derived

### Task C.1: Operation 模型 + Loader 装载

**Files:**
- Create: `src/main/java/com/plantops/ontology/supply/Operation.java`
- Modify: `OntologyIds.java`（`operationId(supplyOrderId, sequenceNr)`）、`OntologyGraph.java`（`operationsById` + `operationsForSupplyOrder(id)`）、`OntologyLoader.java`
- Test: `src/test/java/com/plantops/ontology/OntologyLoaderOperationTest.java`

- [ ] **Step 1: Write failing @QuarkusTest** — 造数：productCode 含 2 道工序（`ProductResourceEntity` sequenceNo=1/2，processTimeSeconds=60，setupTimeMinutes=10）+ 1 个 qty=60 的开放 WO，断言该 SupplyOrder 的 Operation 链 size=2、第 1 步 `productionTimeMinutes == 10 + 60*60/60 == 70`

- [ ] **Step 2: Implement Operation**

```java
public class Operation {
    private String id;
    private String supplyOrderId;
    private int sequenceNr;             // 工序序号（按 sequenceNo 重排为 0..n-1）
    private String operationName;
    private double productionTimeMinutes;
    private LocalDate earliestPossibleStart;
    private LocalDate latestPossibleEnd;
    private boolean infeasible;
    // 全字段 getter/setter；构造器 (id, supplyOrderId, sequenceNr, operationName, productionTimeMinutes)
}
```

- [ ] **Step 3: Implement Loader 装载**

`buildGraph` 中对每个 `SupplyOrder`：

```java
List<ProductResourceEntity> steps = ProductResourceEntity.listInWorkspace().stream()
        .filter(pr -> supplyOrder.getProductCode().equals(pr.productCode))
        .filter(pr -> pr.operationName != null && !pr.operationName.isBlank())
        .collect(Collectors.toMap(pr -> pr.operationName, pr -> pr,
                (a, b) -> a.sequenceNo != null && b.sequenceNo != null && a.sequenceNo <= b.sequenceNo ? a : b,
                LinkedHashMap::new))
        .values().stream()
        .sorted(Comparator.comparing(pr -> pr.sequenceNo != null ? pr.sequenceNo : Integer.MAX_VALUE))
        .toList();
for (int i = 0; i < steps.size(); i++) {
    ProductResourceEntity step = steps.get(i);
    double prodMinutes = step.setupTimeMinutes
            + (step.processTimeSeconds != null
                    ? step.processTimeSeconds.doubleValue() * supplyOrder.getQuantity() / 60.0 : 0.0);
    builder.operation(new Operation(
            OntologyIds.operationId(supplyOrder.getId(), i), supplyOrder.getId(), i,
            step.operationName, prodMinutes));
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit** — `feat(ontology): build operation chains per supply order from product routing`

### Task C.2: 时间窗 derivation 链

**Files:**
- Create: `src/main/java/com/plantops/rol/OperationTimeWindowDerivations.java`
- Modify: `RolEngine.java`（启用 `withMasterPlanRules` 第三组规则 + `applySupplyOrderNeedDateChange`）
- Test: `src/test/java/com/plantops/rol/OperationTimeWindowDerivationTest.java`

- [ ] **Step 1: Write failing test**（纯内存 graph，无 Quarkus）

```java
@Test
void timeWindowsFollowJitChain() {
    // SupplyOrder needDate=6/20, planningStart=6/1；3 步工序 prodTime=1440/2880/1440 分钟（1/2/1 天）
    OntologyGraph g = graphWithOperations(/* 如上构造 */);
    OperationTimeWindowDerivations.recalculate(g, "SO-1", LocalDate.of(2026, 6, 1));
    List<Operation> ops = g.operationsForSupplyOrder("SO-1");
    assertEquals(LocalDate.of(2026, 6, 20), ops.get(2).getLatestPossibleEnd());
    assertEquals(LocalDate.of(2026, 6, 19), ops.get(1).getLatestPossibleEnd()); // 20 − 1天(下游 prod)
    assertEquals(LocalDate.of(2026, 6, 17), ops.get(0).getLatestPossibleEnd()); // 19 − 2天
    assertEquals(LocalDate.of(2026, 6, 1), ops.get(0).getEarliestPossibleStart());
    assertEquals(LocalDate.of(2026, 6, 2), ops.get(1).getEarliestPossibleStart()); // 1 + 1天(上游 prod)
    assertFalse(ops.get(0).isInfeasible());
}

@Test
void flagsInfeasibleWhenWindowEmpty() {
    // needDate=6/2 但链总工时 5 天 → earliest > latest → infeasible
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

```java
public final class OperationTimeWindowDerivations {

    /** 整链重算：latest 自尾倒推（D14），earliest 自首正排；窗口空 → infeasible。 */
    public static void recalculate(OntologyGraph graph, String supplyOrderId, LocalDate planningStart) {
        SupplyOrder so = graph.supplyOrder(supplyOrderId);
        List<Operation> ops = graph.operationsForSupplyOrder(supplyOrderId); // 按 sequenceNr 升序
        if (so == null || ops.isEmpty()) {
            return;
        }
        LocalDate latest = so.getNeedDate() != null ? so.getNeedDate() : planningStart;
        for (int i = ops.size() - 1; i >= 0; i--) {
            ops.get(i).setLatestPossibleEnd(latest);
            latest = latest.minusDays(minutesToDays(ops.get(i).getProductionTimeMinutes()));
        }
        LocalDate earliest = planningStart;
        for (Operation op : ops) {
            op.setEarliestPossibleStart(earliest);
            op.setInfeasible(earliest.isAfter(op.getLatestPossibleEnd()));
            earliest = earliest.plusDays(minutesToDays(op.getProductionTimeMinutes()));
        }
    }

    private static long minutesToDays(double minutes) {
        return Math.max(0, Math.round(Math.ceil(minutes / 1440.0)));
    }

    public static List<Derivation> derivations(OntologyGraph graph) {
        List<Derivation> derivations = new ArrayList<>();
        LocalDate planningStart = graph.periodsOrdered().isEmpty()
                ? LocalDate.now() : graph.periodsOrdered().get(0).getStartDate();
        for (SupplyOrder so : graph.supplyOrdersById().values()) {
            String soId = so.getId();
            derivations.add(new Derivation(
                    Derivation.propertyKey(soId, "operationTimeWindows"),
                    Set.of(Derivation.propertyKey(soId, "needDate")),
                    (g, targetKey) -> recalculate(g, soId, planningStart)));
        }
        return derivations;
    }
}
```

`RolEngine` 新增：

```java
public void applySupplyOrderNeedDateChange(SupplyOrder node, LocalDate needDate) {
    node.setNeedDate(needDate);
    propagateFrom(node.getId(), "needDate");
}
```

Loader 在 Operation 装载后对每个 SupplyOrder 调一次 `recalculate`（初始窗口）。

- [ ] **Step 4: Run — PASS**；启用 B.2 中注释的第三组规则后重跑 `MasterPlanOntologySessionServiceTest`

- [ ] **Step 5: Operations API** — `OperationSnapshotDto(id, supplyOrderId, sequenceNr, operationName, productionTimeMinutes, earliestPossibleStart, latestPossibleEnd, infeasible)`；`GET /api/v1/master-plan/sessions/{sessionId}/supply-orders/{supplyOrderId}/operations`

- [ ] **Step 6: Commit** — `feat(ontology): derive operation time windows with JIT backward chain`

---

## Epic D: S05 合并 — OntologySandbox 基础设施

### Task D.1: 抽取泛型 Sandbox Store

**Files:**
- Create: `src/main/java/com/plantops/scenario/planning/sandbox/OntologySandbox.java`
- Create: `src/main/java/com/plantops/scenario/planning/sandbox/OntologySandboxStore.java`
- Modify: `SchedulingSession.java`、`MasterPlanOntologySession.java`（implements）
- Modify: `SchedulingSessionStore.java`、`MasterPlanOntologySessionStore.java`（继承基类，删除重复 require/TTL 逻辑）
- Test: `src/test/java/com/plantops/scenario/planning/OntologySandboxStoreTest.java`

- [ ] **Step 1: Write failing test** — 用一个测试桩 sandbox 验证：put/require 命中、跨 workspace require 抛 `NotFoundException`、过期 TTL require 抛 `NotFoundException`、`defaultExpiresAt = createdAt + 8h`

- [ ] **Step 2: Implement**

```java
public interface OntologySandbox {
    String sessionId();
    String workspaceId();
    LocalDateTime expiresAt();
}

public abstract class OntologySandboxStore<S extends OntologySandbox> {

    private static final Duration DEFAULT_TTL = Duration.ofHours(8);
    private final Map<String, S> sessions = new ConcurrentHashMap<>();

    protected abstract String notFoundMessage(String sessionId);

    public S put(S session) { sessions.put(session.sessionId(), session); return session; }

    public S require(String sessionId, String workspaceId) {
        S session = sessions.get(sessionId);
        if (session == null
                || !session.workspaceId().equals(workspaceId)
                || session.expiresAt().isBefore(LocalDateTime.now())) {
            throw new NotFoundException(notFoundMessage(sessionId));
        }
        return session;
    }

    public LocalDateTime defaultExpiresAt(LocalDateTime createdAt) { return createdAt.plus(DEFAULT_TTL); }

    public void remove(String sessionId) { sessions.remove(sessionId); }
}
```

两个具体 Store 改为 `extends OntologySandboxStore<SchedulingSession>` / `<MasterPlanOntologySession>`，仅保留 `notFoundMessage` 与既有公开方法签名（含 `require(String)` 单参重载，内部转 `WorkspaceResolver.currentWorkspaceId()`）。`SchedulingSession` 字段是 private + 方法访问器，按 record 风格 `sessionId()` 对齐接口；`MasterPlanOntologySession` 同理。

- [ ] **Step 3: Run — PASS**；回归 `.\mvnw.cmd -q test "-Dtest=SchedulingSessionServiceTest,MasterPlanOntologySessionServiceTest,MasterPlanOntologyConfirmServiceTest"`（S05 既有测试名以实际为准）

- [ ] **Step 4: Commit** — `refactor(planning): unify session stores under OntologySandboxStore`

---

## Epic E: 本体直驱求解评估（spike，只产出文档）

### Task E.1: 评估文档

**Files:**
- Create: `docs/otd-ontology-direct-solve-evaluation.md`
- Modify: `docs/otd-ontology-mapping.md`（D16 决策行）

- [ ] **Step 1:** 阅读 `MasterPlanProblemMapper`（实体→Timefold facts 全链路）并记录：输入实体清单、约束依赖的字段、与本体图字段的覆盖率对照表

- [ ] **Step 2:** 写评估文档，必含章节：
  1. 现状：复用策略（D5）的数据流图（DB → Mapper → Timefold → allocation → ChangeSet → 本体）
  2. 直驱方案：本体图 → problem facts 的字段映射表 + 缺口（如 changeover 矩阵、shift/headcount 不在本体内）
  3. 成本/收益：需新增的本体对象数、Mapper 重写工作量、双轨维护风险
  4. 结论与建议（直驱 / 维持复用 / 混合——给出明确推荐和触发条件）
- [ ] **Step 3:** mapping 文档 D16 行记录结论；**Commit** — `docs(ontology): add direct-solve evaluation and decision record`

---

## Epic F: 回归与文档

### Task F.1: 全量回归 + 文档同步

- [ ] **Step 1:** `.\mvnw.cmd -q test` 全量；与 M2 基线对比（slitting 5 个 404 为已知 V54 遗留，非 M3 回归）
- [ ] **Step 2:** `cd frontend; npm run build` 通过
- [ ] **Step 3:** 更新 `docs/otd-ontology-mapping.md`：PeriodSequence、SRP 装载/回写、Operation 时间窗、Sandbox 合并各对象状态
- [ ] **Step 4:** 更新 `docs/aps-planning-layer.md` §5.7：resources/operations API 与混合桶说明
- [ ] **Step 5:** Commit — `docs(ontology): sync M3 mapping and planning layer docs`

---

## 实施顺序与依赖

```
A.1 → A.2 → A.3（前置：所有 period 映射收敛）
A.3 → B.1 → B.2 → B.3
A.3 → C.1 → C.2（C.2 第三组规则接入 B.2 的 withMasterPlanRules）
D.1 独立，可与 B/C 并行
E.1 独立，可随时进行（建议 B.3 后，有 SRP 实感再评估）
F.1 收尾
```

---

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| 混合桶改变 periodCount，破坏假定 28 的既有测试/前端 | D9 缺省回退 28×1d；混合桶仅在参数显式配置时生效；A.3 跑全量回归 |
| ResourceCalendar 数据缺失 → SRP 全 0 | 容量为 0 不报错；前端表展示 0 值；文档标注数据前置条件 |
| Operation 工序数据噪声（operationName 重复/缺 sequenceNo） | C.1 按 operationName 去重 + sequenceNo 缺失排末位 |
| needDate 传播触发整链重算的性能 | 链长 = 工序数（个位数），整链重算成本可忽略；不做增量 |
| Sandbox 合并破坏 S05 现有 API | D17 仅抽基础设施，公开方法签名不变；Step 3 跑双方既有测试 |
| 直驱评估结论引发范围蔓延 | D16 锁定 M3 只产出文档；实施另立 M4 计划 |

---

## Self-Review

| Spec 要求（M2 预告） | 任务 |
|-----------|------|
| PeriodSequence 混合桶 | Epic A |
| SRP 进 Session + reserved 回写 | Epic B |
| Operation 时间窗 derived | Epic C |
| S05 合并 OntologySandbox | Epic D |
| 本体直驱求解（评估） | Epic E |
| 回归 + 文档 | Epic F |

类型一致性：`PeriodIndex.sequenceFor` 在 A.2 定义、A.3/B.1/B.3 使用；`OntologyIds.srpId` 在 B.1 定义、B.3 使用；`OperationTimeWindowDerivations.derivations` 在 C.2 定义、B.2 引用（实施顺序上 B.2 先注释、C.2 启用）。Placeholder scan：无 TBD；E.1 文档章节已列明。

---

## Execution Handoff

**Plan saved to:** `docs/superpowers/plans/2026-06-10-otd-ontology-master-plan-m3.md`

**Two execution options:**

1. **Subagent-Driven（推荐）** — 每 Task 派子 agent + 审查
2. **Inline Execution** — 本会话连续实施

**Which approach?**
