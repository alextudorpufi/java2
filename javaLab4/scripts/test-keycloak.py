#!/usr/bin/env python3
"""Real Keycloak OIDC authorization-code + PKCE, refresh, logout, and API role checks.

Uses only the standard library. Start compose.keycloak.yaml and the API with the
keycloak profile. This drives the supplied demo login form, never a password grant.
"""
import base64
import hashlib
from html.parser import HTMLParser
from http.cookiejar import CookieJar, DefaultCookiePolicy
import json
import os
import re
import secrets
import sys
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse, parse_qs
from urllib.request import build_opener, HTTPCookieProcessor, HTTPRedirectHandler, Request, urlopen

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
ISSUER = os.environ.get("KEYCLOAK_ISSUER", "http://localhost:8180/realms/java-lab").rstrip("/")
CLIENT = "routing-api-docs"
REDIRECT = "http://localhost:8080/swagger-ui/oauth2-redirect.html"
PASSWORD = os.environ.get("KEYCLOAK_DEMO_PASSWORD", "keycloak-demo-password-123")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class LoginForm(HTMLParser):
    action = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "form" and attrs.get("id") == "kc-form-login":
            self.action = attrs.get("action")


def request(url, expected=200, method="GET", data=None, token=None, form=False):
    headers = {}
    if data is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
        data = (urlencode(data) if form else json.dumps(data)).encode()
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request(url, data=data, headers=headers, method=method)
    try:
        response = urlopen(req, timeout=20)
    except HTTPError as error:
        response = error
    with response:
        body = response.read()
        assert response.code == expected, f"{method} {url}: expected {expected}, got {response.code}; {response.headers.get('WWW-Authenticate', '')}"
        return json.loads(body) if body else None


def login(username):
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    state, nonce = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
    parameters = dict(client_id=CLIENT, response_type="code", scope="openid profile email",
                      redirect_uri=REDIRECT, state=state, nonce=nonce,
                      code_challenge=challenge, code_challenge_method="S256")
    # Keycloak marks localhost cookies Secure. Browsers accept these on localhost HTTP;
    # Python needs an explicit local-only cookie policy to reproduce that behavior.
    protocols = ("https", "wss")
    if urlparse(ISSUER).scheme == "http" and urlparse(ISSUER).hostname in ("localhost", "127.0.0.1"):
        protocols += ("http",)
    jar = CookieJar(DefaultCookiePolicy(secure_protocols=protocols))
    opener = build_opener(HTTPCookieProcessor(jar), NoRedirect())
    with opener.open(ISSUER + "/protocol/openid-connect/auth?" + urlencode(parameters), timeout=20) as response:
        page = response.read().decode()
    parser = LoginForm()
    parser.feed(page)
    assert parser.action, "Keycloak login form missing"
    assert urlparse(parser.action).netloc == urlparse(ISSUER).netloc
    try:
        opener.open(Request(parser.action, data=urlencode({"username": username, "password": PASSWORD}).encode()), timeout=20)
        raise AssertionError("Login did not redirect to the OIDC callback")
    except HTTPError as response:
        assert response.code == 302, f"Login failed: {response.code}"
        location = response.headers["Location"]
        response.close()
    assert location.startswith(REDIRECT + "?")
    callback = parse_qs(urlparse(location).query)
    assert callback["state"] == [state]
    pair = request(ISSUER + "/protocol/openid-connect/token", method="POST", form=True,
                   data=dict(grant_type="authorization_code", client_id=CLIENT,
                             redirect_uri=REDIRECT, code=callback["code"][0], code_verifier=verifier))
    assert all(pair.get(field) for field in ("access_token", "refresh_token", "id_token"))
    # Inspect nonce in the IdP response; API calls below independently verify access-token signatures.
    payload = pair["id_token"].split(".")[1]
    claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    assert claims["nonce"] == nonce
    payload = pair["access_token"].split(".")[1]
    access_claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    audience = access_claims.get("aud", [])
    if isinstance(audience, str):
        audience = [audience]
    assert "routing-api" in audience, f"API audience missing: {audience}"
    assert access_claims.get("typ") == "Bearer", f"Unexpected token type: {access_claims.get('typ')}"
    assert access_claims.get("sub"), "Stable subject missing from access token"
    print(f"PASS {username}: Authorization Code + S256 PKCE returns access, refresh and ID tokens")
    return pair


def refresh(value, expected=200):
    return request(ISSUER + "/protocol/openid-connect/token", expected, "POST", form=True,
                   data=dict(grant_type="refresh_token", client_id=CLIENT, refresh_token=value))


def main():
    with urlopen(BASE + "/swagger-ui/swagger-initializer.js", timeout=20) as response:
        initializer = response.read().decode()
    assert "routing-api-docs" in initializer
    assert re.search(r'"usePkceWithAuthorizationCodeGrant"\s*:\s*true', initializer), "Swagger PKCE must be enabled"
    print("PASS Swagger public-client PKCE configuration")
    discovery = request(ISSUER + "/.well-known/openid-configuration")
    assert discovery["issuer"] == ISSUER
    request(BASE + "/api/v1/vehicles")
    request(BASE + "/api/v1/customers", 401)
    admin, customer, driver = (login(name) for name in ("prof-admin", "prof-customer", "prof-driver"))
    for pair, role in ((admin, "ADMIN"), (customer, "CUSTOMER"), (driver, "DRIVER")):
        identity = request(BASE + "/api/me", token=pair["access_token"])
        assert role in identity["roles"]
    request(BASE + "/api/v1/customers", token=admin["access_token"])
    request(BASE + "/api/v1/customers", 403, token=customer["access_token"])
    request(BASE + "/api/v1/deliveries", token=driver["access_token"])
    request(BASE + "/api/v1/customers", 403, token=driver["access_token"])
    request(BASE + "/api/me", 401, token=admin["id_token"])
    request(BASE + "/api/me", 401, token=admin["refresh_token"])
    scenario = dict(name="OIDC smoke", vehicleIds=[], deliveryIds=[])
    request(BASE + "/api/v1/scenarios", 403, "POST", scenario, customer["access_token"])
    created = request(BASE + "/api/v1/scenarios", 201, "POST", scenario, admin["access_token"])
    request(BASE + "/api/v1/scenarios/" + str(created["id"]), 204, "DELETE", token=admin["access_token"])
    print("PASS API role mapping, ADMIN writes, CUSTOMER/DRIVER restrictions, rejection of ID/refresh tokens")
    rotated = refresh(admin["refresh_token"])
    assert rotated["refresh_token"] != admin["refresh_token"]
    request(BASE + "/api/me", token=rotated["access_token"])
    refresh(admin["refresh_token"], 400)
    print("PASS Keycloak refresh rotation and replay rejection")
    request(ISSUER + "/protocol/openid-connect/logout", 204, "POST", form=True,
            data=dict(client_id=CLIENT, refresh_token=customer["refresh_token"]))
    refresh(customer["refresh_token"], 400)
    print("PASS Keycloak logout revokes refresh capability")
    print("All Keycloak OIDC checks passed.")


if __name__ == "__main__":
    main()
