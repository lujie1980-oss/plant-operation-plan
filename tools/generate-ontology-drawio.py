#!/usr/bin/env python3
"""Generate docs/ontology-domain-model.drawio from ontology domain model."""

from xml.sax.saxutils import escape
import uuid

def cell(cid, parent, value, style, x, y, w, h, vertex=True):
    tag = "mxCell"
    attrs = f'id="{cid}" parent="{parent}" value="{escape(value)}" style="{style}"'
    if vertex:
        attrs += ' vertex="1"'
        geo = f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/>'
    return f'        <{tag} {attrs}>{geo}</{tag}>'

def edge(eid, parent, value, style, source, target, points=None):
    pts = ""
    if points:
        pts = "<mxGeometry relative=\"1\" as=\"geometry\">"
        pts += '<Array as="points">'
        for px, py in points:
            pts += f'<mxPoint x="{px}" y="{py}"/>'
        pts += "</Array></mxGeometry>"
    else:
        pts = '<mxGeometry relative="1" as="geometry"/>'
    return (
        f'        <mxCell id="{eid}" parent="{parent}" value="{escape(value)}" '
        f'style="{style}" edge="1" source="{source}" target="{target}">{pts}</mxCell>'
    )

BOX = "rounded=1;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;"
ENUM = BOX + "fillColor=#f5f5f5;strokeColor=#666666;fontStyle=2;"
DEMAND = BOX + "fillColor=#FFF4E6;strokeColor=#F59E42;fontColor=#92400E;"
SUPPLY = BOX + "fillColor=#ECFDF5;strokeColor=#34D399;fontColor=#065F46;"
FULFILL = BOX + "fillColor=#F5F3FF;strokeColor=#A78BFA;fontColor=#5B21B6;"
PERIOD = BOX + "fillColor=#FDF2F8;strokeColor=#F472B6;fontColor=#9D174D;"
MASTER = BOX + "fillColor=#EFF6FF;strokeColor=#38BDF8;fontColor=#1E40AF;"
ROOT = BOX + "fillColor=#1E293B;strokeColor=#3D9CF5;fontColor=#F8FAFC;fontStyle=1;fontSize=14;"
CONTAINER = "swimlane;whiteSpace=wrap;html=1;startSize=32;fillColor=#F8FAFC;strokeColor=#94A3B8;fontStyle=1;"
ARROW = "endArrow=classic;html=1;rounded=0;strokeColor=#64748B;fontSize=10;"
ARROW_DASH = ARROW + "dashed=1;"

def diagram(name, cells_xml):
    did = str(uuid.uuid4())
    return f'''  <diagram id="{did}" name="{escape(name)}">
    <mxGraphModel dx="1600" dy="900" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1600" pageHeight="1200" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
{cells_xml}
      </root>
    </mxGraphModel>
  </diagram>'''

def page_overview():
    c = []
    c.append(cell("og", "1", "OntologyGraph&lt;br&gt;&lt;font style=&quot;font-size:10px&quot;&gt;聚合根 · 内存只读&lt;/font&gt;", ROOT, 520, 20, 360, 56))
    # Master lane — full routing template chain
    c.append(cell("lane_m", "1", "主数据 master / 工艺模板", CONTAINER + "fillColor=#EFF6FF;", 20, 100, 380, 520))
    master_items = [
        ("p", "Product"), ("sp", "StockingPoint"), ("pisp", "ProductInStockingPoint"),
        ("rt", "Routing"), ("rs", "RoutingStep"),
        ("rsosr", "RoutingStepOnStandardResource"),
        ("rsim", "RoutingStepInputMaterial"), ("rsom", "RoutingStepOutputMaterial"),
    ]
    for i, (lid, lbl) in enumerate(master_items):
        c.append(cell(lid, "lane_m", lbl, MASTER, 20, 48 + i * 56, 340, 44))
    c.append(edge("e_p_pisp", "1", "1:N", ARROW, "p", "pisp"))
    c.append(edge("e_sp_pisp", "1", "", ARROW, "sp", "pisp"))
    c.append(edge("e_pisp_rt", "1", "1:1", ARROW, "pisp", "rt"))
    c.append(edge("e_rt_rs", "1", "1:N", ARROW, "rt", "rs"))
    c.append(edge("e_rs_rsosr", "1", "1:N", ARROW, "rs", "rsosr"))
    c.append(edge("e_rs_rsim", "1", "首道", ARROW, "rs", "rsim"))
    c.append(edge("e_rs_rsom", "1", "末道", ARROW, "rs", "rsom"))

    # Demand lane
    c.append(cell("lane_d", "1", "需求 demand", CONTAINER + "fillColor=#FFF7ED;", 420, 100, 360, 340))
    for i, (lid, lbl) in enumerate([
        ("col", "CustomerOrderLine"), ("cold", "CustomerOrderLineDelivery"),
        ("fc", "ForecastDemand"), ("dem", "Demand"), ("dst", "DemandSourceType")
    ]):
        c.append(cell(lid, "lane_d", lbl, DEMAND if lid != "dst" else ENUM, 20, 48 + i * 54, 320, 44))
    c.append(edge("e_col_cold", "1", "1:N", ARROW, "col", "cold"))
    c.append(edge("e_cold_dem", "1", "1:1", ARROW, "cold", "dem"))
    c.append(edge("e_fc_dem", "1", "1:1", ARROW_DASH, "fc", "dem"))
    c.append(edge("e_dem_dst", "1", "", ARROW, "dem", "dst"))

    # Supply lane
    c.append(cell("lane_s", "1", "供应 supply", CONTAINER + "fillColor=#ECFDF5;", 800, 100, 360, 520))
    items = [
        ("so", "SupplyOrder"), ("pu", "PlanUnit"), ("op", "Operation"),
        ("oosr", "OperationOnStandardResource"), ("oim", "OperationInputMaterial"),
        ("oom", "OperationOutputMaterial"), ("sup", "Supply")
    ]
    for i, (lid, lbl) in enumerate(items):
        c.append(cell(lid, "lane_s", lbl, SUPPLY, 20, 48 + i * 62, 320, 48))
    c.append(edge("e_so_pu", "1", "1:1", ARROW, "so", "pu"))
    c.append(edge("e_pu_op", "1", "1:N", ARROW, "pu", "op"))
    c.append(edge("e_op_oosr", "1", "1:N", ARROW, "op", "oosr"))
    c.append(edge("e_op_oim", "1", "1:N", ARROW, "op", "oim"))
    c.append(edge("e_op_oom", "1", "1:N", ARROW, "op", "oom"))
    c.append(edge("e_oom_sup", "1", "", ARROW, "oom", "sup"))
    c.append(edge("e_oim_dem2", "1", "BOM", ARROW_DASH, "oim", "dem"))
    # Master → runtime projection
    c.append(edge("e_rs_op", "1", "投影", ARROW_DASH, "rs", "op"))
    c.append(edge("e_rsosr_oosr", "1", "", ARROW_DASH, "rsosr", "oosr"))
    c.append(edge("e_rsim_oim", "1", "", ARROW_DASH, "rsim", "oim"))
    c.append(edge("e_rsom_oom", "1", "", ARROW_DASH, "rsom", "oom"))

    # Fulfillment lane
    c.append(cell("lane_f", "1", "满足 fulfillment", CONTAINER + "fillColor=#F5F3FF;", 20, 640, 320, 200))
    for i, (lid, lbl) in enumerate([("ff", "Fulfillment"), ("ft", "FulfillmentType"), ("bd", "BomDependency")]):
        c.append(cell(lid, "lane_f", lbl, FULFILL if lid != "ft" else ENUM, 20, 48 + i * 52, 280, 44))
    c.append(edge("e_dem_ff", "1", "N:M", ARROW, "dem", "ff"))
    c.append(edge("e_sup_ff", "1", "", ARROW, "sup", "ff"))
    c.append(edge("e_ff_ft", "1", "", ARROW, "ff", "ft"))
    c.append(edge("e_bd_so", "1", "parent→child", ARROW_DASH, "bd", "so"))

    # Period lane
    c.append(cell("lane_t", "1", "期间 period / scheduling", CONTAINER + "fillColor=#FDF2F8;", 1180, 100, 340, 400))
    for i, (lid, lbl) in enumerate([
        ("per", "Period"), ("pispp", "ProductInStockingPointPeriod"),
        ("srp", "StandardResourcePeriod"), ("ss", "SchedulingSlot")
    ]):
        c.append(cell(lid, "lane_t", lbl, PERIOD, 20, 48 + i * 72, 300, 52))
    c.append(edge("e_pisp_pispp", "1", "1:N", ARROW_DASH, "pisp", "pispp"))
    c.append(edge("e_per_pispp", "1", "", ARROW, "per", "pispp"))
    c.append(edge("e_per_srp", "1", "", ARROW, "per", "srp"))
    c.append(edge("e_ss_srp", "1", "optimize", ARROW_DASH, "ss", "srp"))

    c.append(cell("note1", "1", "&lt;b&gt;前端主粒度&lt;/b&gt;: CustomerOrderLineDelivery (COLD-*)&lt;br&gt;PISP→Routing→RoutingStep→RSOSR/RSIM/RSOM · 装载投影为 Operation 族&lt;br&gt;SupplyOrder.id = WorkOrderEntity.workOrderNo", 
                  "text;html=1;strokeColor=none;fillColor=none;align=left;fontSize=11;fontColor=#64748B;", 20, 860, 720, 60))
    return diagram("01-总览", "\n".join(c))

def page_demand():
    c = []
    c.append(cell("col", "1", "CustomerOrderLine&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;id: COL-{so}-{line}&lt;br&gt;productCode, orderQty&lt;/font&gt;", DEMAND, 80, 120, 220, 80))
    c.append(cell("cold", "1", "CustomerOrderLineDelivery&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;id: COLD-{so}-{line}-{seq}&lt;br&gt;deliveryQty, latestDesiredDate&lt;/font&gt;", DEMAND, 400, 120, 240, 80))
    c.append(cell("fc", "1", "ForecastDemand&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;id: FC-{forecastId}&lt;/font&gt;", DEMAND, 80, 320, 220, 70))
    c.append(cell("dem", "1", "Demand&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;统一锚点 · productCode · needDate · priority&lt;/font&gt;", DEMAND, 720, 180, 260, 90))
    c.append(cell("dst", "1", "DemandSourceType&lt;br&gt;CUSTOMER_DELIVERY | FORECAST | BOM_COMPONENT", ENUM, 720, 340, 260, 70))
    c.append(cell("jpa", "1", "SalesOrderLineEntity&lt;br&gt;(JPA 投影)", "rounded=1;dashed=1;fillColor=#F1F5F9;strokeColor=#94A3B8;html=1;", 80, 480, 200, 50))
    c.append(edge("e1", "1", "1 : N (现 1:1)", ARROW, "col", "cold"))
    c.append(edge("e2", "1", "1 : 1", ARROW, "cold", "dem"))
    c.append(edge("e3", "1", "1 : 1", ARROW, "fc", "dem"))
    c.append(edge("e4", "1", "", ARROW, "dem", "dst"))
    c.append(edge("e5", "1", "投影", ARROW_DASH, "jpa", "col"))
    return diagram("02-需求侧", "\n".join(c))

def page_supply():
    c = []
    x, w = 60, 200
    chain = [
        ("so", "SupplyOrder&lt;br&gt;id = workOrderNo", 60),
        ("pu", "PlanUnit", 180),
        ("op", "Operation&lt;br&gt;productionDuration, planned*", 300),
        ("oosr", "OperationOnStandardResource&lt;br&gt;resourcePriority", 440),
        ("oim", "OperationInputMaterial", 580),
        ("oom", "OperationOutputMaterial", 720),
        ("sup", "Supply&lt;br&gt;SUP-* / INV / SHORT", 860),
    ]
    ids = []
    for lid, lbl, y in chain:
        c.append(cell(lid, "1", lbl, SUPPLY, x, y, w, 70))
        ids.append(lid)
    for i in range(len(ids) - 1):
        c.append(edge(f"e{i}", "1", "", ARROW, ids[i], ids[i + 1]))
    c.append(cell("dem", "1", "Demand&lt;br&gt;BOM_COMPONENT", DEMAND, 920, 580, 160, 60))
    c.append(edge("eoim", "1", "refs", ARROW, "oim", "dem"))
    c.append(cell("wo", "1", "WorkOrderEntity (JPA)", "rounded=1;dashed=1;fillColor=#F1F5F9;strokeColor=#94A3B8;html=1;", 60, 40, 200, 40))
    c.append(edge("ewo", "1", "1:1", ARROW_DASH, "wo", "so"))
    c.append(cell("pr", "1", "ProductResourceEntity → Operation + OOSR", "rounded=1;dashed=1;fillColor=#F1F5F9;strokeColor=#94A3B8;html=1;", 300, 40, 280, 40))
    return diagram("03-供应制造", "\n".join(c))

def page_fulfillment():
    c = []
    c.append(cell("dem", "1", "Demand", DEMAND, 80, 200, 140, 50))
    c.append(cell("ff", "1", "Fulfillment&lt;br&gt;demandId · supplyId · quantity", FULFILL, 320, 180, 200, 70))
    c.append(cell("sup", "1", "Supply", SUPPLY, 620, 200, 140, 50))
    c.append(cell("inv", "1", "INVENTORY_PEG&lt;br&gt;优先", ENUM, 320, 320, 120, 44))
    c.append(cell("wo", "1", "WORK_ORDER_PEG", ENUM, 460, 320, 130, 44))
    c.append(cell("sh", "1", "SHORTAGE_PEG", ENUM, 600, 320, 120, 44))
    c.append(edge("e1", "1", "", ARROW, "dem", "ff"))
    c.append(edge("e2", "1", "", ARROW, "ff", "sup"))
    c.append(cell("so_p", "1", "SupplyOrder 父", SUPPLY, 80, 480, 140, 50))
    c.append(cell("so_c", "1", "SupplyOrder 子", SUPPLY, 620, 480, 140, 50))
    c.append(cell("bd", "1", "BomDependency&lt;br&gt;派生 · 非 JPA 直读", FULFILL, 350, 460, 180, 60))
    c.append(edge("e3", "1", "parent", ARROW, "bd", "so_p"))
    c.append(edge("e4", "1", "child", ARROW, "bd", "so_c"))
    c.append(cell("note", "1", "挂接顺序: 库存 → 工单 → 缺口", "text;html=1;strokeColor=none;fillColor=none;fontSize=12;", 300, 80, 280, 30))
    return diagram("04-满足链", "\n".join(c))

def page_master():
    c = []
    # Header chain: PISP → Routing → RoutingStep
    c.append(cell("pisp", "1", "ProductInStockingPoint&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;id: PISP-{product}&lt;/font&gt;", MASTER, 80, 80, 220, 60))
    c.append(cell("rt", "1", "Routing&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;id: RT-{pispId}&lt;/font&gt;", MASTER, 360, 80, 200, 60))
    c.append(cell("rs", "1", "RoutingStep&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;id: RS-{pispId}-{seq}&lt;br&gt;sequenceNo, operationName&lt;/font&gt;", MASTER, 620, 80, 260, 70))
    c.append(edge("e_pisp_rt", "1", "1:1", ARROW, "pisp", "rt"))
    c.append(edge("e_rt_rs", "1", "1:N", ARROW, "rt", "rs"))
    # Children of RoutingStep
    children = [
        ("rsosr", "RoutingStepOnStandardResource&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;RSOSR- · resourcePriority&lt;/font&gt;", 280),
        ("rsim", "RoutingStepInputMaterial&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;RSIN- · componentQtyPer&lt;/font&gt;", 420),
        ("rsom", "RoutingStepOutputMaterial&lt;br&gt;&lt;font style=&quot;font-size:9px&quot;&gt;RSOUT- · outputQtyPer&lt;/font&gt;", 560),
    ]
    for lid, lbl, y in children:
        c.append(cell(lid, "1", lbl, MASTER, 620, y, 260, 70))
        c.append(edge(f"e_rs_{lid}", "1", "1:N", ARROW, "rs", lid))
    c.append(cell("proj_lbl", "1", "装载投影（OntologyLoader / 每 SupplyOrder）", "text;html=1;strokeColor=none;fillColor=none;fontStyle=1;fontSize=12;fontColor=#64748B;", 80, 700, 360, 30))
    # Runtime targets
    runtime = [
        ("op", "Operation", 80, 760),
        ("oosr", "OperationOnStandardResource", 300, 760),
        ("oim", "OperationInputMaterial", 560, 760),
        ("oom", "OperationOutputMaterial", 820, 760),
    ]
    for lid, lbl, x, y in runtime:
        c.append(cell(lid, "1", lbl + " (运行时)", SUPPLY, x, y, 200, 50))
    c.append(edge("proj_rs_op", "1", "", ARROW_DASH, "rs", "op"))
    c.append(edge("proj_rsosr_oosr", "1", "", ARROW_DASH, "rsosr", "oosr"))
    c.append(edge("proj_rsim_oim", "1", "", ARROW_DASH, "rsim", "oim"))
    c.append(edge("proj_rsom_oom", "1", "", ARROW_DASH, "rsom", "oom"))
    c.append(cell("mpr", "1", "MasterPlanRoutingProjector&lt;br&gt;MaterialEntity + ProductResourceEntity + BomComponentEntity", "rounded=1;dashed=1;fillColor=#F1F5F9;strokeColor=#94A3B8;html=1;fontSize=10;", 80, 200, 400, 50))
    c.append(edge("e_mpr_rt", "1", "投影", ARROW_DASH, "mpr", "rt"))
    c.append(cell("note_rs", "1", "RSIM 通常挂首道工序 · RSOM 通常挂末道工序", "text;html=1;strokeColor=none;fillColor=none;fontSize=10;fontColor=#64748B;", 620, 660, 280, 30))
    return diagram("05-主数据工艺", "\n".join(c))

def page_period():
    c = []
    c.append(cell("per", "1", "Period&lt;br&gt;P-{n}", PERIOD, 400, 80, 160, 50))
    c.append(cell("pisp", "1", "ProductInStockingPoint", MASTER, 120, 220, 200, 50))
    c.append(cell("pispp", "1", "ProductInStockingPointPeriod&lt;br&gt;onHand · shortage", PERIOD, 120, 360, 220, 60))
    c.append(cell("sr", "1", "StandardResource", MASTER, 680, 220, 180, 50))
    c.append(cell("srp", "1", "StandardResourcePeriod&lt;br&gt;capacity · reserved", PERIOD, 680, 360, 200, 60))
    c.append(cell("ss", "1", "SchedulingSlot&lt;br&gt;↔ TimeSlot 1:1", PERIOD, 400, 500, 200, 50))
    c.append(edge("e1", "1", "1:N", ARROW, "per", "pispp"))
    c.append(edge("e2", "1", "", ARROW, "pisp", "pispp"))
    c.append(edge("e3", "1", "1:N", ARROW, "per", "srp"))
    c.append(edge("e4", "1", "", ARROW, "sr", "srp"))
    c.append(edge("e5", "1", "optimize 回写", ARROW_DASH, "ss", "srp"))
    return diagram("06-期间产能", "\n".join(c))

def page_loader():
    c = []
    loaders = [("ol", "OntologyLoader", 80), ("scl", "SupplyChainLoader", 240), ("fl", "FulfillmentLoader", 400), ("bdd", "BomDependencyDerivation", 560)]
    prev = None
    for lid, lbl, y in loaders:
        c.append(cell(lid, "1", lbl, "rounded=1;fillColor=#E0E7FF;strokeColor=#6366F1;html=1;", 200, y, 220, 44))
        if prev:
            c.append(edge(f"le_{prev}", "1", "", ARROW, prev, lid))
        prev = lid
    c.append(cell("og", "1", "OntologyGraph", ROOT, 220, 620, 200, 50))
    c.append(edge("log", "1", "", ARROW, "bdd", "og"))
    c.append(cell("ui", "1", "需求满足页", DEMAND, 520, 80, 140, 44))
    c.append(cell("cold", "1", "CustomerOrderLineDelivery", DEMAND, 520, 160, 200, 44))
    c.append(cell("chain", "1", "OrderFulfillmentChainDto", FULFILL, 520, 240, 200, 44))
    c.append(cell("sb", "1", "DeliveryPlanningSandbox", FULFILL, 520, 320, 200, 44))
    c.append(cell("opt", "1", "OR-Tools / Timefold", SUPPLY, 520, 400, 180, 44))
    for a, b in [("ui", "cold"), ("cold", "chain"), ("chain", "sb"), ("sb", "opt")]:
        c.append(edge(f"fe_{a}_{b}", "1", "", ARROW, a, b))
    c.append(edge("fog", "1", "scoped", ARROW_DASH, "cold", "og"))
    return diagram("07-装载与前端", "\n".join(c))

def page_ids():
    c = []
    rows = [
        ("COL-", "CustomerOrderLine"), ("COLD-", "CustomerOrderLineDelivery"),
        ("DEM-COLD-/FC-/BOM-", "Demand"), ("RT-/RS-/RSOSR-", "Routing / RoutingStep / RSOSR"),
        ("RSIN-/RSOUT-", "RoutingStep 投料 / 产出"), ("PU-/OP-/OOSR-", "PlanUnit/Operation/OOSR"),
        ("SUP-/INV-/SHORT-", "Supply"), ("FF-", "Fulfillment"), ("BOM-DEP-", "BomDependency"),
        ("PISP-/PISPP-", "物料点/期间"), ("SRP-", "资源期间"), ("{res}-D{n}", "SchedulingSlot"),
    ]
    for i, (prefix, ent) in enumerate(rows):
        y = 60 + i * 52
        c.append(cell(f"p{i}", "1", prefix, "rounded=0;fillColor=#FEF3C7;strokeColor=#D97706;html=1;fontFamily=Courier New;", 120, y, 160, 36))
        c.append(cell(f"e{i}", "1", ent, "rounded=0;fillColor=#F8FAFC;strokeColor=#CBD5E1;html=1;", 320, y, 280, 36))
        c.append(edge(f"ed{i}", "1", "", ARROW, f"p{i}", f"e{i}"))
    return diagram("08-ID约定", "\n".join(c))

pages = [
    page_overview(), page_demand(), page_supply(), page_fulfillment(),
    page_master(), page_period(), page_loader(), page_ids(),
]

xml = '''<?xml version="1.0" encoding="UTF-8"?>
<mxfile host="app.diagrams.net" modified="2026-06-10T00:00:00.000Z" agent="Plant Operation Plan" version="22.1.0" type="device" pages="8">
''' + "\n".join(pages) + "\n</mxfile>\n"

out = __file__.replace("tools\\generate-ontology-drawio.py", "docs\\ontology-domain-model.drawio").replace(
    "tools/generate-ontology-drawio.py", "docs/ontology-domain-model.drawio")
with open(out, "w", encoding="utf-8") as f:
    f.write(xml)
print("Wrote", out)
