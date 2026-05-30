#!/usr/bin/env python3
"""Import parallel operation rules from Excel (兼容原「U型线清单.xlsx」格式)."""

from __future__ import annotations

import argparse
import pathlib
import sys
import urllib.request

DEFAULT_XLSX = pathlib.Path(r"d:\OneDrive\桌面\数据模板\不常更新\U型线清单.xlsx")


def main() -> int:
    parser = argparse.ArgumentParser(description="Import parallel operation rules")
    parser.add_argument("--workspace", default="te")
    parser.add_argument("--file", type=pathlib.Path, default=DEFAULT_XLSX)
    parser.add_argument("--replace", action="store_true", default=True)
    parser.add_argument("--base-url", default="http://localhost:8080")
    args = parser.parse_args()

    if not args.file.is_file():
        print(f"File not found: {args.file}", file=sys.stderr)
        return 1

    url = (
        f"{args.base_url.rstrip('/')}/api/v1/master-data/excel/parallel-operation-import"
        f"?replace={str(args.replace).lower()}"
    )
    req = urllib.request.Request(
        url,
        data=args.file.read_bytes(),
        method="POST",
        headers={
            "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "X-Workspace-Id": args.workspace,
        },
    )
    with urllib.request.urlopen(req) as resp:
        print(resp.read().decode("utf-8"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
