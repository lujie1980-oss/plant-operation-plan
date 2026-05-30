# 演示数据工具

## 从 Excel 生成 `factory-demo.json`

标准 Demo 来源：`演示需求-数据准备.xlsx`（工艺路径 / 设备 / 物料 / 库存 四个 Sheet）。

1. 将 Excel 复制到本目录为 `demo-source.xlsx`（或任意路径）。
2. 安装依赖：`pip install pandas openpyxl`
3. 执行：

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan
python -X utf8 tools/parse_demo_excel.py
# 或指定文件
python -X utf8 tools/parse_demo_excel.py --xlsx "D:\path\演示需求-数据准备.xlsx"
```

输出：`src/main/resources/sample-data/factory-demo.json`

## 数据结构说明

| 区块 | 内容 |
|------|------|
| `salesOrderLines` | 演示销售订单（成品A） |
| `bomComponents` | 多层级 BOM（成品A → 电子/机加半成品 → 原材料） |
| `inventory` | 自「库存」Sheet |
| `resources` / `lines` | 自「设备」Sheet |
| `workOrders` | 按「自制」物料自动生成多级工单树（`parentWorkOrderNo`） |

重启后端（`quarkus:dev`）且数据库为空时会自动加载新 JSON。
