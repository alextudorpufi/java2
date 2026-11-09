#!/usr/bin/env python3
"""Run real curl requests against the homework API. Uses an existing depot ID."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid
import xml.etree.ElementTree as ET

base = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
depot_id = int(sys.argv[2]) if len(sys.argv) > 2 else 1
api_prefix = (sys.argv[3] if len(sys.argv) > 3 else "/api/v1").rstrip("/")
created = []
checks = 0


def request(method, path, expected, payload=None, headers=None):
    global checks
    with tempfile.TemporaryDirectory(prefix="routing-curl-") as directory:
        header_file = Path(directory) / "headers"
        command = ["curl", "--silent", "--show-error", "--max-time", "15", "--request", method,
                   "--dump-header", str(header_file), "--write-out", "\n%{http_code}", base + path]
        for key, value in (headers or {"Accept": "application/json"}).items():
            command += ["--header", f"{key}: {value}"]
        if payload is not None:
            command += ["--header", "Content-Type: application/json", "--data", json.dumps(payload)]
        output = subprocess.check_output(command, text=True)
        body, status = output.rsplit("\n", 1)
        assert int(status) in ((expected,) if isinstance(expected, int) else expected), (
            f"{method} {path}: expected {expected}, got {status}: {body}")
        response_headers = {}
        for line in header_file.read_text().splitlines():
            if ":" in line:
                key, value = line.split(":", 1)
                response_headers[key.lower()] = value.strip()
    checks += 1
    print(f"PASS {method} {path} -> {status}")
    return response_headers, body


def create(resource, payload):
    headers, body = request("POST", f"{api_prefix}/{resource}", 201, payload)
    result = json.loads(body)
    path = f"{api_prefix}/{resource}/{result['id']}"
    created.append(path)
    assert headers["location"] == base + path
    return result, path


try:
    customer, customer_path = create("customers", dict(
        name="Curl Homework", email=f"curl-{uuid.uuid4().hex}@example.com", phone="0700000000",
        address="Bucharest", latitude=44.4268, longitude=26.1025))
    vehicle_payload = dict(name="Curl van", capacity=100, depotId=depot_id, available=True)
    vehicle, vehicle_path = create("vehicles", vehicle_payload)
    delivery_payload = dict(customerId=customer["id"], quantity=10, notes="Curl test",
                            timestamp="2026-11-02T10:00:00Z")
    delivery, delivery_path = create("deliveries", delivery_payload)
    links = customer["_links"]
    assert links["self"]["href"] == f"{base}/api/v1/customers/{customer['id']}"
    _, body = request("GET", links["deliveries"]["href"].removeprefix(base), 200)
    assert [row["id"] for row in json.loads(body)] == [delivery["id"]]
    headers, _ = request("GET", f"/api/customers/{customer['id']}", 200)
    assert headers["link"] == f'<{base}/api/v1/customers/{customer["id"]}>; rel="successor-version"'
    request("GET", "/api/v2/customers", 404)
    scenario_payload = dict(name="Morning", vehicleIds=[vehicle["id"]], deliveryIds=[delivery["id"]])
    scenario, scenario_path = create("scenarios", scenario_payload)
    shared, shared_path = create("scenarios", dict(scenario_payload, name="Alternative"))

    for resource in ("customers", "vehicles", "deliveries", "scenarios"):
        headers, body = request("GET", api_prefix + "/" + resource, 200)
        assert isinstance(json.loads(body), list)
        _, body = request("GET", api_prefix + "/" + resource, 304, headers={"If-None-Match": headers["etag"]})
        assert body == ""
    for path in (vehicle_path, delivery_path, scenario_path, shared_path):
        request("GET", path, 200)

    headers, body = request("GET", scenario_path, 200)
    tag = headers["etag"]
    assert json.loads(body)["vehicleIds"] == [vehicle["id"]]
    for validator in (tag, '"other", ' + tag, "*"):
        _, body = request("GET", scenario_path, 304, headers={"If-None-Match": validator})
        assert body == ""
    headers, body = request("GET", scenario_path, 200, headers={"Accept": "application/xml"})
    assert "application/xml" in headers["content-type"]
    assert ET.fromstring(body).findtext("name") == "Morning"
    xml_tag = headers["etag"]
    assert xml_tag != tag
    _, body = request("GET", scenario_path, 304,
                      headers={"Accept": "application/xml", "If-None-Match": xml_tag})
    assert body == ""
    request("GET", scenario_path, 200, headers={"Accept": "application/xml", "If-None-Match": tag})

    request("PUT", vehicle_path, 200, dict(vehicle_payload, name="Updated van"))
    assert json.loads(request("GET", vehicle_path, 200)[1])["name"] == "Updated van"
    request("PUT", delivery_path, 200, dict(delivery_payload, quantity=20))
    assert json.loads(request("GET", delivery_path, 200)[1])["quantity"] == 20
    request("PUT", scenario_path, 200, dict(scenario_payload, name="Evening"))
    _, body = request("GET", scenario_path, 200, headers={"If-None-Match": tag})
    assert json.loads(body)["name"] == "Evening"

    for resource in ("vehicles", "deliveries", "scenarios"):
        request("POST", api_prefix + "/" + resource, 400, {})
        request("GET", api_prefix + "/" + resource + "/999999999", 404)
        request("DELETE", api_prefix + "/" + resource + "/999999999", 404)
    request("POST", api_prefix + "/vehicles", 400, dict(vehicle_payload, capacity=0))
    request("POST", api_prefix + "/vehicles", 404, dict(vehicle_payload, depotId=999999999))
    request("POST", api_prefix + "/deliveries", 404, dict(delivery_payload, customerId=999999999))
    request("PUT", scenario_path, 404, dict(scenario_payload, name="Invalid", vehicleIds=[999999999]))
    assert json.loads(request("GET", scenario_path, 200)[1])["name"] == "Evening"
    for path in (vehicle_path, delivery_path, customer_path):
        request("DELETE", path, 409)

    request("DELETE", scenario_path, 204)
    request("GET", scenario_path, 404, headers={"If-None-Match": "*"})
    request("GET", vehicle_path, 200)
    request("GET", delivery_path, 200)
    request("GET", shared_path, 200)
    request("PUT", shared_path, 200, dict(name="Empty", vehicleIds=[], deliveryIds=[]))
    request("DELETE", vehicle_path, 204)
    request("DELETE", delivery_path, 204)
    request("GET", vehicle_path, 404)
    request("GET", delivery_path, 404)

    _, body = request("GET", "/v3/api-docs", 200)
    operation = json.loads(body)["paths"][api_prefix + "/scenarios/{id}"]["get"]
    assert operation["summary"] == "Read a scenario" and "304" in operation["responses"]
    _, body = request("GET", "/v3/api-docs/v1", 200)
    assert "/api/v1/customers/{id}/deliveries" in json.loads(body)["paths"]
    request("GET", "/swagger-ui/index.html", 200)
finally:
    # Reverse creation order removes scenario memberships before their resources.
    for path in reversed(created):
        request("DELETE", path, (204, 404))

print(f"All {checks} homework API curl checks passed.")
