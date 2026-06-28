#!/usr/bin/env python3
"""Print Flyway-compatible CRC32 checksum (signed int32) for a migration file."""
import sys
import zlib
from pathlib import Path


def flyway_checksum(path: Path) -> int:
    """Match Flyway CRC32 over raw file bytes (same as Java Files.readAllBytes)."""
    data = path.read_bytes()
    c = zlib.crc32(data) & 0xFFFFFFFF
    return c - 2**32 if c >= 2**31 else c


if __name__ == "__main__":
    for p in sys.argv[1:] or ["src/main/resources/db/migration/V53__jinghua_mrp_slitting.sql"]:
        path = Path(p)
        print(f"{path}: {flyway_checksum(path)}")
