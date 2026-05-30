"""Create 10 test sales order lines in workspace te."""
import json
from datetime import date, timedelta
from urllib import request

BASE = "http://localhost:8080/api/v1"
WORKSPACE = "te"
ORDER_NO = "TE-TEST-20260529"

PRODUCTS = [
    "1-2411379-1",
    "1-2411498-6",
    "1-2411498-8",
    "1-2495112-1",
    "1-2496074-1",
    "1-2498255-1",
    "1-2498255-2",
    "1-2500507-1",
    "1-2500507-2",
    "1-2500529-1",
]


def post_json(path: str, payload: dict) -> dict:
    body = json.dumps(payload).encode("utf-8")
    req = request.Request(
        f"{BASE}{path}",
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Workspace-Id": WORKSPACE,
        },
    )
    with request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> None:
    today = date(2026, 5, 29)
    created = []
    for i, product in enumerate(PRODUCTS):
        line_no = (i + 1) * 10
        due = today + timedelta(days=14 + i * 7)
        payload = {
            "id": None,
            "salesOrderNo": ORDER_NO,
            "salesOrderLineNo": line_no,
            "customerCode": "TE-CUST",
            "productCode": product,
            "orderQty": 100 + i * 50,
            "uom": "EA",
            "promiseDate": due.isoformat(),
            "dueDate": due.isoformat(),
            "priority": 5,
            "expediteLevel": 0,
            "status": "OPEN",
            "scheduleLockFlag": False,
        }
        row = post_json("/master-data/sales-orders", payload)
        created.append(
            {
                "line": line_no,
                "productCode": product,
                "orderQty": payload["orderQty"],
                "dueDate": due.isoformat(),
                "id": row.get("id"),
            }
        )

    # trigger work order generation for all open orders
    req = request.Request(
        f"{BASE}/demand/work-orders/generate",
        data=b"{}",
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Workspace-Id": WORKSPACE,
        },
    )
    wo_result = json.loads(request.urlopen(req, timeout=120).read().decode("utf-8"))

    print(
        json.dumps(
            {
                "salesOrderNo": ORDER_NO,
                "linesCreated": len(created),
                "orders": created,
                "workOrderGeneration": wo_result,
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
