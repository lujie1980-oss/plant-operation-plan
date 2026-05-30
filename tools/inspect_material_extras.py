"""Inspect extra (unlabeled) columns in the 物料基本资料 sheet to find hidden BOM/copart info."""
from pathlib import Path
import openpyxl

PATH = Path(r"d:\OneDrive\工作\01 售前\03 已失败\202501_02_马勒#汽车零部件\POC需求资料.xlsx")
wb = openpyxl.load_workbook(PATH, data_only=True, read_only=True)
ws = wb["物料基本资料"]
rows = list(ws.iter_rows(values_only=True))
non_empty = [r for r in rows if any(c is not None and str(c).strip() != "" for c in r)]
print("Header row 0:", non_empty[0])
print("Header row 1:", non_empty[1])
print("Header row 2:", non_empty[2])
print("Header row 3:", non_empty[3])
print("len of body rows:", len(non_empty[4:]))
print()
print("Sample body rows (first 15):")
for r in non_empty[4:19]:
    print("  ", r)
print()
print("Rows with extra col 13/14 populated:")
populated = [r for r in non_empty[4:] if len(r) > 13 and (r[13] is not None or r[14] is not None)]
print(f"  count: {len(populated)}")
for r in populated[:20]:
    print("  part=", r[1], "col13=", r[13], "col14=", r[14] if len(r) > 14 else None)
