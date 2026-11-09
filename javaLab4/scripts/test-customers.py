#!/usr/bin/env python3
"""Exercise a running customer API using curl. Requires Python 3 and curl."""
import json
import subprocess
import sys
import uuid


base = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
api_prefix = (sys.argv[2] if len(sys.argv) > 2 else "/api/v1").rstrip("/")
endpoint = base + api_prefix + "/customers"


def request(method, url, expected, body=None):
    command = ["curl", "--silent", "--show-error", "--max-time", "15",
               "--request", method, "--write-out", "\n%{http_code}", url]
    if body is not None:
        command += ["--header", "Content-Type: application/json", "--data", json.dumps(body)]
    output = subprocess.check_output(command, text=True)
    content, status = output.rsplit("\n", 1)
    assert int(status) == expected, f"{method} {url}: expected {expected}, got {status}: {content}"
    print(f"PASS {method} {url} -> {status}")
    return json.loads(content) if content else None


payload = dict(name="Curl Customer", email=f"curl-{uuid.uuid4().hex}@example.com",
               phone="+40700000001", address="Bucharest", latitude=44.4268, longitude=26.1025)
created = request("POST", endpoint, 201, payload)
url = f"{endpoint}/{created['id']}"
try:
    assert created.items() >= payload.items()
    assert "deliveries" not in created
    assert request("GET", url, 200) == created
    assert any(row["id"] == created["id"] for row in request("GET", endpoint, 200))
    request("POST", endpoint, 409, payload)
    updated = dict(payload, name="Updated Customer", address="Cluj-Napoca")
    result = request("PUT", url, 200, updated)
    assert result["id"] == created["id"] and result.items() >= updated.items()
    assert request("GET", url, 200) == result
    for invalid in ({}, dict(payload, email="invalid"), dict(payload, name=" "),
                    dict(payload, latitude=91), dict(payload, longitude=-181),
                    dict(payload, latitude=1.1234567), dict(payload, address="a" * 501)):
        request("POST", endpoint, 400, invalid)
    request("PUT", url, 400, {})
    assert request("GET", url, 200) == result
    request("GET", endpoint + "/invalid-id", 400)
finally:
    request("DELETE", url, 204)

request("GET", url, 404)
request("PUT", url, 404, payload)
request("DELETE", url, 404)
assert all(row["id"] != created["id"] for row in request("GET", endpoint, 200))
print("All customer CRUD curl checks passed.")
