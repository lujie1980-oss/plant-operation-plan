#!/usr/bin/env python3
"""Deprecated alias — use import_parallel_operations.py instead."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

if __name__ == "__main__":
    print(
        "Note: import_u_line_pairs.py is deprecated; use import_parallel_operations.py",
        file=sys.stderr,
    )
    script = Path(__file__).resolve().with_name("import_parallel_operations.py")
    raise SystemExit(subprocess.call([sys.executable, str(script), *sys.argv[1:]]))
