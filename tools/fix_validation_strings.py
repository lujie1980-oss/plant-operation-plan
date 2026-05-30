from pathlib import Path

p = Path(__file__).resolve().parents[1] / "src/main/java/com/plantops/masterdata/MasterDataValidationService.java"
lines = p.read_text(encoding="utf-8", errors="replace").splitlines()

fixes = {
    89: '                        "产品工艺 (productCode, resourceId) 重复",',
    99: '                        "产品工艺引用的 resourceId 不存在",',
    109: '                        "工艺 processTimeSeconds <= 0，产能计算可能异常",',
    132: '                        "日历可用/不可用产能分钟数为负",',
    141: "        // 路由涉及资源无日历时告警",
    142: "        for (String rid : ProductionResourceEntity.routingResourceIds()) {",
    149: '                        "资源无任何日历记录，产能分析可能不准",',
    163: '                        "资源 runRatePerHour <= 0，产能计算可能异常",',
    178: '                        "库存 onhandQty 为负，MRP/齐套可能异常",',
    210: '                        "产线引用的资源不存在",',
    221: '                        "同一资源被多条产线引用",',
    236: '                        "换型 setupMinutes 为负",',
    250: '                        "换型 from=to 时建议 setup 为 0",',
    280: '                        "BOM 不能自引用",',
    293: '                        "BOM componentQty <= 0，MRP/齐套将异常",',
    308: '                        "关键子件无工艺路线，主计划/MRP 可能无法展开",',
    332: '                    "BOM 存在循环引用，将无法展开 MRP/主计划",',
    347: '                        "销售订单行 (salesOrderNo, salesOrderLineNo) 重复",',
    349: '                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, SO_LINE_DUP, "订单行重复"));',
    360: '                        "销售订单 productCode 为空",',
    362: '                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, SO_PRODUCT_EMPTY, "产品为空"));',
    372: '                        "销售订单 orderQty <= 0",',
    374: '                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, SO_QTY_NONPOSITIVE, "数量<=0"));',
    384: '                        "销售订单 dueDate 为空",',
    386: '                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, SO_DUEDATE_EMPTY, "交期为空"));',
    398: '                        "成品无工艺路线，无法生成工单",',
    400: '                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, PRODUCT_NO_ROUTING, "无工艺路线"));',
    405: '                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, BOM_CYCLE, "BOM 循环"));',
    410: '                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, BOM_QTY_NONPOSITIVE, "BOM 数量异常"));',
}

for lineno, content in fixes.items():
    lines[lineno - 1] = content

p.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("fixed validation service")
