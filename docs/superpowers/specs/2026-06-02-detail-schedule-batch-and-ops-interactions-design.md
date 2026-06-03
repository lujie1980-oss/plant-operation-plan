## 背景与目标

在「生产排程 / 生产排程页面」的右上「待排产批次」区域增加更强的交互能力：

- 批次行支持右键菜单：选中批次、单独排该批次（在当前 Session 内插入并推演，不清空其它内容）。
- 批次行支持拖拽到甘特图：落到某条产线后自动把批次排入（当前 Session 内）。
- 批次列表右侧增加「批次工序步骤列表」：从当前 Session 预览中展示该批次的工序。
- 工序列表支持：选择工序并单排产；工序右键菜单“指定产线…”→弹窗从可选产线中选择→将工序排入该产线的最早可排产时间（在当前 Session 内）。

非目标：

- 不做新的“只包含单批次”的 Session；一切操作都发生在当前 Session。
- 不引入 Timefold 新策略，仅使用现有 Session patch + simulate 增量推演能力。
- “最早可排”先按前端插入算法实现，不在第一版下沉后端推荐（后续可迭代）。

## 术语与现状

- **Session**：`useScheduleSession` 创建的排程会话，`preview.operations` 包含本次推演的全部工序（含未排程 scheduled=false）。
- **批次列表**：`PendingScheduleBatchList` 使用 `api.schedulingBatches.listKitting()` 仅展示批次主数据（批次号、工单、齐套、可排）。
- **甘特拖拽**：`MachineScheduleGantt` 支持对单个工序拖拽并回调 `onDragCommit`，页面调用 `simulate(stepPatches)` 生效。

## 交互与行为规范

### 1) 批次右键菜单

在批次表格行上右键弹出菜单（context menu）：

- **选中批次**：等价于点击行（高亮选中，右侧工序列表刷新）。
- **单独排该批次**：
  - 语义：在当前 Session 内，把该批次的工序插入队列并触发一次 `simulate(stepPatches)`。
  - 当批次工序当前尚未分配产线时，弹出“选择目标产线”对话框（一次选择，批次内工序默认同产线）。
  - 当工序已分配产线：尊重既有产线；仍按“最早可排”插入到队列合适位置（见“最早可排插入算法”）。

### 2) 批次拖拽到甘特图

- 批次行支持拖拽（HTML5 Drag&Drop）。drag payload：`{ type: 'batch', batchNo }`。
- 甘特图每条产线的轨道区域可 drop：
  - drop 命中 `lineId` + 横向位置换算 `dropMinute`。
  - 行为：把该批次的工序插入该 `lineId` 的队列，并一次性调用 `simulate(stepPatches)`。
  - 默认：批次内工序都排入同一条产线（drop 的那条）。
  - 插入顺序：按 `operationSeq` 升序；对于每道工序生成 patch：
    - `stepId` = operationId
    - `lineId` = dropLineId
    - `sequenceOnLine`：用 `dropMinute` + 队列当前任务的 mid-point 算出插入点；后续工序使用小的 minute 偏移保证稳定顺序（例如 +1 分钟递增）。

### 3) 批次工序步骤列表（批次右侧）

把右上面板拆为左右两栏：

- 左：待排产批次表（原有）。
- 右：批次工序表（来源：当前 Session 的 `preview.operations`，按 `batchNo` 过滤；包含未排程工序）。

工序表字段建议：

- 工序序号（operationSeq）
- 工序名称（operationName）
- 是否已排（scheduled）
- 当前产线（lineId）
- 最早可排（earliestStartMinute，可选展示）

无 Session 时：右侧提示“请先创建 Session 后查看工序并操作排产”。

### 4) 工序单排产

工序表行支持：

- 点击选中工序（高亮）。
- 按钮/双击触发 **单排产（最早可排）**：
  - 若该工序已有产线：直接按“最早可排插入算法”插入该产线并 simulate。
  - 若无产线：转入“指定产线…”对话框（见下一节）。

### 5) 工序右键：指定产线…

在工序表行右键菜单：

- **单排产（最早可排）**
- **指定产线…**

选择“指定产线…”：

- 弹窗显示“可选产线”下拉列表（来源：后端接口，根据该 operation 在当前 Session 的 `OperationAssignment.acceptsLine(line)` 过滤）。
- 确认后按“最早可排插入算法”排入目标产线并 simulate。

## 最早可排插入算法（前端第一版）

目标：把某个 operation 插入某条产线队列的“最早可排产时间”附近，使其尽可能早，同时不破坏现有队列顺序的直觉。

输入：

- 目标工序：`preview.operation`（含 `earliestStartMinute`）
- 目标产线：`lineId`
- 当前甘特同线任务列表（按 startMinute 排序）

算法：

- 取 `t0 = op.earliestStartMinute ?? 0`
- 找到同线任务中第一个 `task.startMinute >= t0` 的位置 i
- `sequenceOnLine = i + 1`（1-based）；若没找到则放末尾（len+1）
- 生成 patch 并调用 `simulate([{ stepId, lineId, sequenceOnLine }])`

说明：

- 这是“插入到最早可开始点附近”的启发式；最终时间由后端链式赋时/约束校验决定。
- 若后续发现体验不佳，可迭代为后端返回推荐插入点（第二版）。

## 后端接口（可选产线）

新增接口：

- `GET /api/v1/planning/schedule-sessions/{sessionId}/operations/{operationId}/candidate-lines`
  - 返回：`List<String>` lineIds
  - 逻辑：在 session.schedule 中定位该 `OperationAssignment`，遍历 schedule.lines，筛 `op.acceptsLine(line)` 为 true
  - 错误：
    - sessionId 不存在：404
    - operationId 不在 session 中：404

前端使用：

- 工序“指定产线…”弹窗打开时加载一次；失败则提示并降级为“全部产线”（可选：直接禁止确认）。

## 前端组件与状态拆分（建议）

- `PendingScheduleBatchList`
  - 新增：`onContextMenu(row, pos)` / 内部自带 context menu
  - 新增：批次行 `draggable` + `onDragStart`
- 新组件：`BatchOperationListPanel`
  - props：`selectedBatchNo`, `preview`, `onSelectOperation`, `onScheduleOperationEarliest`, `onAssignLine`
  - 负责：展示工序列表 + 行右键菜单
- 新组件：`AssignLineDialog`
  - props：`open`, `operation`, `sessionId`, `onConfirm(lineId)`, `onClose`
  - 负责：加载 candidate lines 并选择

页面：`DetailSchedulePage.tsx`

- 保存：`selectedBatch`, `selectedOperationId`
- 按批次过滤工序列表：`preview.operations.filter(op => op.batchNo === selectedBatchNo)`
- 调度动作最终都走：`simulate(stepPatches)`

## 测试与验收（DoD）

前端：

- 批次右键菜单可用：选中批次、单独排该批次。
- 批次可拖拽到甘特：释放后触发一次 simulate，甘特更新可见该批次工序被插入目标线队列。
- 批次右侧工序列表随批次选中变化；无 Session 时提示正确。
- 工序单排产：已分配产线时可直接插入；未分配时弹窗指定产线后插入。
- 工序右键“指定产线…”弹窗可正确展示“可选产线”；确认后插入并推演。

后端：

- candidate-lines 接口在 session+operation 存在时返回非空/可为空列表，但不报错。
- sessionId/operationId 不存在返回 404。

## 兼容性与后续迭代

- 后续可扩展：
  - 后端返回“推荐插入点（sequenceOnLine）”与“推荐 earliest drop minute”
  - 批次内工序支持按工艺路线分配不同产线（需要更复杂策略）
  - 批次拖拽时可选择“仅排未排工序”开关

