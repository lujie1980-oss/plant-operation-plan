"""Convert extracted Mahle POC data into the factory-demo.json sample-data format.

输出文件： src/main/resources/sample-data/factory-demo.json

转换规则：
- 资源(equip)：使用 equip_name 作为 resourceId，所有资源归入工作中心 (ws_id) 区域。
  runRatePerHour 取该资源所有工艺路线 process_time 的中位数换算 (3600 / process_time)。
  在制造件高度集中的瓶颈段（CB/BE/CL）默认标记 bottleneck=true。
- 工艺(routing)：保留所有 (part_id, equip_name) 工序，每行 setup_time/60 进位为分钟。
  当一个产品有多个工序时，主计划用 firstResult，因此选第一道工序 (sequ_num 最小)。
- BOM：原数据全部为自引用，过滤后输出空 BOM；可在主数据维护页继续添加。
- 库存：按 (warehouse_name + section) 形成库存点。
- 销售订单：从需求来源抽取 part_id、due_date、order_qty 非零的行；按行号自动编号。
  POC 中所有交期都在 2026-02 区间，需要按 today 偏移到未来窗口。
- 产线、班次人员、换型矩阵、模具等：以工作中心 / 资源为蓝本生成最小可用集合。
"""
from __future__ import annotations

import json
import statistics
from collections import OrderedDict
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

EXTRACTED = Path(__file__).parent / "poc_extracted.json"
OUTPUT = Path(__file__).parent.parent / "src" / "main" / "resources" / "sample-data" / "factory-demo.json"


def safe_int(v: Any, default: int = 0) -> int:
    if v is None:
        return default
    try:
        return int(float(v))
    except (TypeError, ValueError):
        return default


def safe_float(v: Any, default: float = 0.0) -> float:
    if v is None:
        return default
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def to_str(v: Any) -> str | None:
    if v is None:
        return None
    if isinstance(v, float) and v.is_integer():
        return str(int(v))
    return str(v).strip() or None


def parse_date(v: Any) -> date | None:
    if v is None:
        return None
    if isinstance(v, date):
        return v
    s = str(v).strip()
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d", "%Y/%m/%d"):
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            pass
    return None


def main() -> None:
    data = json.loads(EXTRACTED.read_text(encoding="utf-8"))

    # ---------- 资源 ----------
    equip_id_to_name: dict[str, str] = {}
    resources_out = []
    ws_to_area: dict[str, str] = {}
    seen_resources: set[str] = set()
    for r in data["资源"]:
        equip_id = r.get("equip_id")
        equip_name = to_str(r.get("equip_name"))
        if equip_id is None or equip_name is None:
            continue
        equip_id_to_name[to_str(equip_id)] = equip_name
        ws_id = to_str(r.get("ws_id")) or "冷却车间"
        ws_to_area[ws_id] = ws_id
        if equip_name in seen_resources:
            continue
        seen_resources.add(equip_name)
        resources_out.append({
            "resourceId": equip_name,
            "areaId": ws_id,
            "bottleneck": True,
            "runRatePerHour": 60,
        })

    # ---------- 工作中心 ----------
    work_centers = data.get("工作中心", [])

    # ---------- 工艺 ----------
    process_times_per_resource: dict[str, list[float]] = {}
    setup_times_per_pr: dict[tuple[str, str], int] = {}
    product_resources_seen: set[tuple[str, str]] = set()
    product_first_step: dict[str, dict] = {}
    routing_full: list[dict] = []  # 仅用于后续可扩展
    for row in data["工艺资料"]:
        part_id = to_str(row.get("part_id"))
        equip_id = row.get("equip_id")
        if part_id in (None, "part_id") or equip_id is None:
            continue
        equip_name = equip_id_to_name.get(to_str(equip_id))
        if equip_name is None:
            # POC 数据中可能引用未在资源表的 equip_id，则用 equip_id 字符串代替
            equip_name = to_str(equip_id)
            if equip_name not in seen_resources:
                seen_resources.add(equip_name)
                resources_out.append({
                    "resourceId": equip_name,
                    "areaId": "冷却车间",
                    "bottleneck": False,
                    "runRatePerHour": 60,
                })
        process_time = safe_float(row.get("process_time"), 60)
        setup_time = safe_int(row.get("setup_time"), 0)
        sequ_num = safe_int(row.get("sequ_num"), 1)
        if process_time > 0:
            process_times_per_resource.setdefault(equip_name, []).append(process_time)
        setup_minutes = max(0, (setup_time + 59) // 60)
        key = (part_id, equip_name)
        if key not in product_resources_seen:
            product_resources_seen.add(key)
            setup_times_per_pr[key] = setup_minutes
        else:
            setup_times_per_pr[key] = max(setup_times_per_pr[key], setup_minutes)
        # 记录每个 part 的最早工序对应资源
        existing = product_first_step.get(part_id)
        if existing is None or sequ_num < existing["sequ_num"]:
            product_first_step[part_id] = {
                "sequ_num": sequ_num,
                "equip_name": equip_name,
                "process_time": process_time,
                "setup_minutes": setup_minutes,
            }
        routing_full.append({
            "part_id": part_id,
            "sequ_num": sequ_num,
            "operation_id": row.get("operation_id"),
            "operation_name": row.get("operation_name"),
            "equip_name": equip_name,
            "setup_minutes": setup_minutes,
            "process_time_seconds_per_unit": process_time,
        })

    # 更新资源 runRatePerHour 为中位数 3600 / median(process_time)
    for res in resources_out:
        times = process_times_per_resource.get(res["resourceId"])
        if not times:
            continue
        median_pt = statistics.median(times)
        if median_pt > 0:
            rate = round(3600 / median_pt, 2)
            res["runRatePerHour"] = max(1.0, rate)

    # ---------- product_resources（保留完整工艺路由，按 sequ_num 排序） ----------
    # 同一产品在同一资源上若有多道工序，保留 sequ_num 最小的一行。
    product_resources_out: list[dict] = []
    pr_seen: set[tuple[str, str]] = set()
    routing_full_sorted = sorted(routing_full, key=lambda r: (r["part_id"], r["sequ_num"]))
    for step in routing_full_sorted:
        key = (step["part_id"], step["equip_name"])
        if key in pr_seen:
            continue
        pr_seen.add(key)
        product_resources_out.append({
            "productCode": step["part_id"],
            "resourceId": step["equip_name"],
            "setupTimeMinutes": step["setup_minutes"],
            "sequenceNo": step["sequ_num"],
            "operationName": step.get("operation_name"),
            "processTimeSeconds": step["process_time_seconds_per_unit"],
        })

    # ---------- BOM ----------
    # POC Excel 的 BOM 关系实际记录在「物料基本资料」表无表头的第 13/14 列：
    #   - col 13 (parent_part_id) = 父件品号
    #   - col 14 (parent_qty)     = 父件批量大小（仅用于参考，组件用量按 1:1 处理）
    # 「物料清单」表的行全部为自引用，只是声明品号存在，不构成真实父子关系，忽略。
    bom_out: list[dict] = []
    bom_seen: set[tuple[str, str]] = set()
    for row in data["物料基本资料"]:
        component = to_str(row.get("part_id"))
        parent = to_str(row.get("parent_part_id"))
        if not component or not parent or component == parent:
            continue
        key = (parent, component)
        if key in bom_seen:
            continue
        bom_seen.add(key)
        bom_out.append({
            "parentProductCode": parent,
            "componentProductCode": component,
            "componentQty": 1.0,
            "isCriticalComponent": True,
        })

    # ---------- 库存 ----------
    inventory_out: list[dict] = []
    inv_seen: set[tuple[str, str]] = set()
    for row in data["库存"]:
        part_id = to_str(row.get("part_id"))
        if not part_id or part_id == "part_id":
            continue
        warehouse = row.get("warehouse_name") or "W01"
        section = row.get("section")
        stocking_point = warehouse if not section else f"{warehouse}-{section}"
        key = (stocking_point, part_id)
        if key in inv_seen:
            continue
        inv_seen.add(key)
        qty = safe_float(row.get("unallocate_qty"), 0)
        inventory_out.append({
            "stockingPointCode": stocking_point,
            "productCode": part_id,
            "onhandQty": qty,
            "reservedQty": 0,
        })

    # ---------- 销售订单（需求来源） ----------
    # 确定日期偏移：原数据集中在 2026-02-06 ~ 2026-02-13；将最早交期映射到 (today + 1)
    raw_orders = []
    for r in data["需求来源"]:
        part_id = to_str(r.get("part_id"))
        if not part_id or part_id == "part_id":
            continue
        order_qty = safe_float(r.get("order_qty"), 0)
        if order_qty <= 0:
            continue
        dd = parse_date(r.get("due_date"))
        if dd is None:
            continue
        raw_orders.append({
            "demand_order_id": to_str(r.get("demand_order_id")),
            "customer_id": to_str(r.get("customer_id")),
            "due_date": dd,
            "part_id": part_id,
            "part_name": r.get("part_name"),
            "order_qty": order_qty,
            "order_type": r.get("order_type"),
        })

    if raw_orders:
        min_due = min(o["due_date"] for o in raw_orders)
        target_min = date.today() + timedelta(days=1)
        offset = (target_min - min_due).days
    else:
        offset = 0

    sales_orders_out: list[dict] = []
    line_counter: dict[str, int] = {}
    for i, o in enumerate(raw_orders, start=1):
        shifted = o["due_date"] + timedelta(days=offset)
        order_no = o["demand_order_id"] or f"DO-MAHLE-{i:04d}"
        line_counter.setdefault(order_no, 0)
        line_counter[order_no] += 10
        line_no = line_counter[order_no]
        sales_orders_out.append({
            "salesOrderNo": order_no,
            "salesOrderLineNo": line_no,
            "customerCode": o["customer_id"] or "MAHLE",
            "productCode": o["part_id"],
            "orderQty": o["order_qty"],
            "promiseDate": shifted.isoformat(),
            "dueDate": shifted.isoformat(),
            "priority": 3 if o.get("order_type") == "R" else 5,
            "expediteLevel": 0,
            "status": "OPEN",
        })

    # ---------- 产线（按每个资源生成一个最小产线，便于产能与人员展示） ----------
    lines_out: list[dict] = []
    for res in resources_out[:8]:  # 选前 8 个资源生成线
        lines_out.append({
            "lineId": f"LINE-{res['resourceId']}",
            "areaId": res["areaId"],
            "resourceId": res["resourceId"],
            "lineMinHeadcount": 2,
            "lineCapacityPerShift": 480,
        })

    # ---------- 换型矩阵 ----------
    changeover_out: list[dict] = []  # 留空，主数据页可维护

    # ---------- workOrders ----------
    work_orders_out: list[dict] = []  # 由 WorkOrderGenerationService 自动生成

    out_data = OrderedDict({
        "_meta": {
            "source": "Mahle POC (POC需求资料.xlsx)",
            "generated_at": datetime.now().isoformat(timespec="seconds"),
            "demand_date_offset_days": offset,
            "earliest_due_date": (date.today() + timedelta(days=1)).isoformat()
                if raw_orders else None,
            "sales_order_count": len(sales_orders_out),
            "resource_count": len(resources_out),
            "product_resource_count": len(product_resources_out),
            "inventory_count": len(inventory_out),
            "work_centers": work_centers,
        },
        "salesOrderLines": sales_orders_out,
        "bomComponents": bom_out,
        "inventory": inventory_out,
        "resources": resources_out,
        "productResources": product_resources_out,
        "lines": lines_out,
        "changeoverMatrix": changeover_out,
        "workOrders": work_orders_out,
    })

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(out_data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUTPUT}")
    for k in ("salesOrderLines", "bomComponents", "inventory", "resources", "productResources", "lines"):
        print(f"  {k}: {len(out_data[k])}")


if __name__ == "__main__":
    main()
