"""Obtain a JWT for the existing curl smoke checks (or opt into a dev server)."""
import json
import os
import subprocess


def security_options(base):
    if os.environ.get("API_DEV_MODE") == "true":
        return []
    username = os.environ.get("API_USERNAME", "admin")
    password = os.environ.get("API_PASSWORD")
    if not password:
        raise SystemExit("Set API_USERNAME and API_PASSWORD for an ADMIN account, or API_DEV_MODE=true for dev.")
    response = subprocess.check_output([
        "curl", "--silent", "--show-error", "--fail-with-body", "--max-time", "15",
        "--header", "Content-Type: application/json", "--data-binary", "@-", base + "/login"
    ], input=json.dumps({"username": username, "password": password}), text=True)
    token = json.loads(response)["accessToken"]
    return ["--header", f"Authorization: Bearer {token}"]
