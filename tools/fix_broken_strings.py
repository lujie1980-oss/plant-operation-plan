from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/java"

fixes = {
    "com/plantops/scenario/ScenarioComparisonService.java": {
        155: '                case UNCONSTRAINED -> "无产能约束";',
    },
    "com/plantops/scenario/CapacityService.java": {
        76: "    /** 按指定主计划版本分析产能负荷（用于场景对比）。 */",
        203: '                    kpi("cap_resource_count", "瓶颈机台", 0, "台", "info"),',
        205: '                    kpi("cap_avg_util", "平均利用率", 0, "%", "info"),',
        207: '                    kpi("cap_overload", "超载区间", 0, "个", "ok"),',
        209: '                    kpi("cap_critical", "高负荷区间", 0, "个", "ok")));',
        228: '                kpi("cap_resource_count", "瓶颈机台", resources, "台", "info"),',
        230: '                kpi("cap_avg_util", "平均利用率", avgUtil, "%", avgSeverity),',
        232: '                kpi("cap_overload", "超载区间", overload, "个", overload > 0 ? "danger" : "ok"),',
        234: '                kpi("cap_critical", "高负荷区间(>90%)", critical, "个", critical > 0 ? "warn" : "ok")));',
    },
    "com/plantops/api/MasterDataResource.java": {
        693: '            throw new WebApplicationException(label + " 不存在", Response.Status.NOT_FOUND);',
    },
    "com/plantops/scenario/CapacityService.java": {
        332: '                        "主计划"));',
        391: '                                "需求测算"));',
        340: "        // 已有主计划版本时，空桶表示该日该机台无排产，不应再回退到需求测算",
    },
}

for rel, line_fixes in fixes.items():
    p = ROOT / rel
    lines = p.read_text(encoding="utf-8", errors="replace").splitlines()
    for lineno, content in line_fixes.items():
        lines[lineno - 1] = content
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("fixed", rel)
