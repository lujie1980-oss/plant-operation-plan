#!/usr/bin/env python3
"""Import business rule Excel files (changeover + parallel operations) into workspace."""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import urllib.request

DEFAULT_CHANGEOVER = pathlib.Path(r"d:\OneDrive\桌面\换型时间.xlsx")
DEFAULT_PARALLEL = pathlib.Path(r"d:\OneDrive\桌面\数据模板\不常更新\U型线清单.xlsx")


def post_xlsx(url: str, workspace: str, file_path: pathlib.Path) -> dict:
    req = urllib.request.Request(
        url,
        data=file_path.read_bytes(),
        method="POST",
        headers={
            "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "X-Workspace-Id": workspace,
        },
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Import business rules from Excel")
    parser.add_argument("--workspace", default="te")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--changeover", type=pathlib.Path, default=DEFAULT_CHANGEOVER)
    parser.add_argument("--parallel", type=pathlib.Path, default=DEFAULT_PARALLEL)
    parser.add_argument("--skip-changeover", action="store_true")
    parser.add_argument("--skip-parallel", action="store_true")
    parser.add_argument("--replace", action="store_true", default=True)
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    replace = str(args.replace).lower()
    exit_code = 0

    if not args.skip_changeover:
        if not args.changeover.is_file():
            print(f"Changeover file not found: {args.changeover}", file=sys.stderr)
            return 1
        url = f"{base}/api/v1/master-data/excel/changeover-import?replace={replace}"
        result = post_xlsx(url, args.workspace, args.changeover)
        print("Changeover:", json.dumps(result, ensure_ascii=False))
        if result.get("errors"):
            exit_code = 1

    if not args.skip_parallel:
        if not args.parallel.is_file():
            print(f"Parallel operations file not found: {args.parallel}", file=sys.stderr)
            return 1
        url = f"{base}/api/v1/master-data/excel/parallel-operation-import?replace={replace}"
        result = post_xlsx(url, args.workspace, args.parallel)
        print("Parallel operations:", json.dumps(result, ensure_ascii=False))
        if result.get("errors"):
            exit_code = 1

    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
