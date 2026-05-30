#!/usr/bin/env python3
"""
从「演示需求-数据准备.xlsx」生成 factory-demo.json（多层级 BOM + 工单树）。

用法:
  python tools/parse_demo_excel.py
  python tools/parse_demo_excel.py --xlsx path/to/file.xlsx
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_XLSX = Path(__file__).resolve().parent / "demo-source.xlsx"
OUT_JSON = ROOT / "src/main/resources/sample-data/factory-demo.json"

OP_PATTERN = re.compile(r"线|设备|机|SMT|DIP|炉|床|焊|切片|委外|人员|包装|治具|老化|pcs|H|公斤", re.I)


def cell(row, idx: int) -> str:
    if idx >= len(row):
        return ""
    v = row.iloc[idx]
    if pd.isna(v):
        return ""
    return str(v).strip()


def parse_qty(raw: str) -> float:
    if not raw:
        return 1.0
    m = re.search(r"([\d.]+)", raw.replace("*", "x"))
    return float(m.group(1)) if m else 1.0


def is_operation(name: str) -> bool:
    if not name:
        return False
    if any(k in name for k in ("半成品", "原材料", "成品", "钢板", "钢材")):
        return False
    return bool(OP_PATTERN.search(name))


def parse_process_route(df) -> list[dict]:
    """解析「工艺路径」sheet 为多层级 BOM。"""
    components: list[dict] = []
    fg_parent: str | None = None
    block_parent: str | None = None  # 当前工艺块父件（成品或半成品）

    for i in range(1, len(df)):
        row = df.iloc[i]
        c0, c1, c2, c3, c4 = cell(row, 0), cell(row, 1), cell(row, 2), cell(row, 3), cell(row, 4)

        # 新成品或半成品块标题（列0有物料名）
        if c0 and c0 not in ("资源/材料",):
            if c0 == "成品A" or c0.endswith("成品"):
                fg_parent = c0
                block_parent = c0
            else:
                block_parent = c0

        if c3 and not is_operation(c3) and c2 not in ("制造", "副资源"):
            parent = block_parent or fg_parent
            if not parent:
                continue
            # in 行、工序名下的投料（SMTB/切割等）、或列2为空的平铺子件
            is_input = c2 == "in" or (not c2 and not c1) or (c2 and c1)
            if is_input:
                components.append({
                    "parentProductCode": parent,
                    "componentProductCode": c3,
                    "componentQty": parse_qty(c4),
                    "isCriticalComponent": True,
                })

    # 去重
    seen = set()
    unique = []
    for c in components:
        key = (c["parentProductCode"], c["componentProductCode"])
        if key not in seen:
            seen.add(key)
            unique.append(c)
    return unique


def parse_materials(df) -> dict[str, dict]:
    products = {}
    for i in range(1, len(df)):
        row = df.iloc[i]
        code = cell(row, 1)
        if not code:
            continue
        products[code] = {
            "safetyStock": parse_qty(cell(row, 2)),
            "makeBuy": cell(row, 3),
            "unit": cell(row, 4),
        }
    return products


def parse_inventory(df) -> list[dict]:
    inv = []
    for i in range(1, len(df)):
        row = df.iloc[i]
        code = cell(row, 1)
        if not code:
            continue
        inv.append({
            "stockingPointCode": "WH-01",
            "productCode": code,
            "onhandQty": parse_qty(cell(row, 4)),
            "reservedQty": 0,
        })
    return inv


def parse_equipment(df) -> tuple[list[dict], list[dict]]:
    resources = []
    lines = []
    seen = set()
    for i in range(1, len(df)):
        row = df.iloc[i]
        name = cell(row, 1)
        if not name or name in seen:
            continue
        seen.add(name)
        rid = name.replace(" ", "-")
        resources.append({
            "resourceId": rid,
            "areaId": "AREA-1",
            "bottleneck": "线" in name,
            "runRatePerHour": 80,
        })
        lines.append({
            "lineId": f"L-{rid}",
            "areaId": "AREA-1",
            "resourceId": rid,
            "lineMinHeadcount": 2,
            "lineCapacityPerShift": 480,
        })
    return resources, lines


def default_resource(product: str) -> str:
    if "电子" in product:
        return "SMT1"
    if "机加" in product:
        return "切割机"
    if "成品" in product or product.startswith("产品"):
        return "组装1线"
    return "组装1线"


def build_work_orders(
    products: dict[str, dict],
    components: list[dict],
    sales_orders: list[dict],
) -> list[dict]:
    make_products = {p for p, m in products.items() if m.get("makeBuy") == "自制"}
    # 在 BOM 中作为父件出现的自制件也需要工单
    for c in components:
        if c["parentProductCode"] in products and products[c["parentProductCode"]].get("makeBuy") == "自制":
            make_products.add(c["parentProductCode"])
        if c["componentProductCode"] in products and products[c["componentProductCode"]].get("makeBuy") == "自制":
            make_products.add(c["componentProductCode"])

    children_map: dict[str, list[dict]] = {}
    for c in components:
        children_map.setdefault(c["parentProductCode"], []).append(c)

    work_orders: list[dict] = []
    seq = 1

    def add_wo(wo_no, parent_wo, so, line_no, product, qty, res):
        nonlocal seq
        work_orders.append({
            "workOrderNo": wo_no,
            "parentWorkOrderNo": parent_wo,
            "salesOrderNo": so,
            "salesOrderLineNo": line_no,
            "productCode": product,
            "quantity": qty,
            "resourceId": res.replace(" ", "-"),
            "sequenceNo": seq,
        })
        seq += 1

    def expand(parent_wo: str, parent_product: str, multiplier: float, so: str, line_no: int):
        for comp in children_map.get(parent_product, []):
            child = comp["componentProductCode"]
            need = float(comp["componentQty"]) * multiplier
            if child not in make_products:
                continue
            child_wo = f"{parent_wo}-{child}"
            res = default_resource(child)
            add_wo(child_wo, parent_wo, so, line_no, child, need, res)
            expand(child_wo, child, need, so, line_no)

    for so in sales_orders:
        fg = so["productCode"]
        qty = float(so["orderQty"])
        root = f"WO-{so['salesOrderNo']}-{so['salesOrderLineNo']}"
        add_wo(root, None, so["salesOrderNo"], so["salesOrderLineNo"], fg, qty, default_resource(fg))
        expand(root, fg, qty, so["salesOrderNo"], so["salesOrderLineNo"])

    return work_orders


def build_product_resources(work_orders: list[dict]) -> list[dict]:
    seen = set()
    out = []
    for wo in work_orders:
        key = (wo["productCode"], wo["resourceId"])
        if key in seen:
            continue
        seen.add(key)
        out.append({
            "productCode": wo["productCode"],
            "resourceId": wo["resourceId"],
            "setupTimeMinutes": 30,
        })
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--xlsx", type=Path, default=DEFAULT_XLSX)
    parser.add_argument("--out", type=Path, default=OUT_JSON)
    args = parser.parse_args()

    xl = pd.ExcelFile(args.xlsx)
    route_df = pd.read_excel(args.xlsx, sheet_name=xl.sheet_names[0], header=None)
    equip_df = pd.read_excel(args.xlsx, sheet_name=xl.sheet_names[1], header=None)
    mat_df = pd.read_excel(args.xlsx, sheet_name=xl.sheet_names[2], header=None)
    inv_df = pd.read_excel(args.xlsx, sheet_name=xl.sheet_names[3], header=None)

    components = parse_process_route(route_df)
    products = parse_materials(mat_df)
    inventory = parse_inventory(inv_df)
    resources, lines = parse_equipment(equip_df)

    sales_order_lines = [
        {
            "salesOrderNo": "SO-DEMO-001",
            "salesOrderLineNo": 10,
            "customerCode": "CUST-DEMO",
            "productCode": "成品A",
            "orderQty": 100,
            "promiseDate": "2026-06-15",
            "dueDate": "2026-06-15",
            "priority": 1,
            "expediteLevel": 0,
            "status": "OPEN",
        },
        {
            "salesOrderNo": "SO-DEMO-002",
            "salesOrderLineNo": 10,
            "customerCode": "CUST-DEMO",
            "productCode": "成品A",
            "orderQty": 50,
            "promiseDate": "2026-06-20",
            "dueDate": "2026-06-22",
            "priority": 2,
            "status": "OPEN",
        },
        {
            "salesOrderNo": "SO-DEMO-003",
            "salesOrderLineNo": 10,
            "customerCode": "CUST-DEMO-2",
            "productCode": "成品A",
            "orderQty": 30,
            "promiseDate": "2026-06-25",
            "dueDate": "2026-06-28",
            "priority": 3,
            "status": "OPEN",
        },
    ]

    work_orders = build_work_orders(products, components, sales_order_lines)
    product_resources = build_product_resources(work_orders)

    demo = {
        "meta": {
            "source": "演示需求-数据准备.xlsx",
            "description": "标准Demo多层级BOM（成品A→电子/机加半成品→原材料）",
            "generatedBy": "tools/parse_demo_excel.py",
        },
        "salesOrderLines": sales_order_lines,
        "bomComponents": components,
        "inventory": inventory,
        "resources": resources,
        "productResources": product_resources,
        "lines": lines,
        "changeoverMatrix": [],
        "workOrders": work_orders,
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(demo, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {args.out}")
    print(f"  BOM: {len(components)}")
    print(f"  Work orders: {len(work_orders)}")
    print(f"  Sales orders: {len(sales_order_lines)}")
    for c in components[:12]:
        print(f"    {c['parentProductCode']} <- {c['componentProductCode']} x{c['componentQty']}")


if __name__ == "__main__":
    main()
