from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/java/com/plantops/scenario"

fixes = {
    "ScenarioComparisonService.java": [
        ('cap_avg_util', '            new ScenarioMetricDto("cap_avg_util", "平均利用率", "%", "bar"),'),
        ('cap_overload', '            new ScenarioMetricDto("cap_overload", "超载区间", "个", "bar"),'),
        ('cap_critical', '            new ScenarioMetricDto("cap_critical", "高负荷区间", "个", "bar"),'),
        ('mp_total_wo', '            new ScenarioMetricDto("mp_total_wo", "已排工单", "张", "bar"),'),
        ('mp_total_load', '            new ScenarioMetricDto("mp_total_load", "总负荷", "分钟", "bar"),'),
        ('solve_duration', '            new ScenarioMetricDto("solve_duration", "求解耗时", "秒", "bar"));'),
    ],
}

for fname, rules in fixes.items():
    p = ROOT / fname
    lines = p.read_text(encoding="utf-8", errors="replace").splitlines()
    for i, line in enumerate(lines):
        for key, repl in rules:
            if key in line and "ScenarioMetricDto" in line:
                lines[i] = repl
                break
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("fixed", fname)
