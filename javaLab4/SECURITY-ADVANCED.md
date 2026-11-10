# Access/refresh tokens and Keycloak OIDC

There are two selectable authentication modes. The default uses database users
and locally issued tokens. The `keycloak` profile delegates login, user accounts,
roles, refresh, and logout to Keycloak. Both enforce the same API and method-level
permissions. Select one mode for a demonstration; their tokens are not interchangeable.

## Local access and refresh tokens

`POST /login` now returns:

```json
{
  "accessToken": "signed JWT",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "random opaque token",
  "refreshExpiresIn": 604799
}
```

The access token is sent in `Authorization: Bearer ...`. The refresh token is
sent only to `POST /refresh` or `POST /logout`, in a JSON `refreshToken` field.
It cannot authenticate API requests. Refresh responses have the same shape as
login responses and use `Cache-Control: no-store`.

Each login starts a separate refresh session with a seven-day absolute expiry.
A refresh consumes the old token and issues a replacement with a new access JWT.
Only SHA-256 hashes of cryptographically random 256-bit refresh tokens are stored.
Flyway V4 creates `refresh_sessions` and `refresh_tokens`; JDBC row locking makes
concurrent rotation safe on H2 and PostgreSQL. The stored session expiry never
extends during rotation. Client applications must serialize refresh requests and
replace both stored tokens after a successful refresh.

Reusing a consumed token returns 401 and commits revocation of the entire refresh
session, including its latest replacement. This does not affect other logins.
Expired tokens and disabled/deleted accounts cannot refresh. Each rotation uses
the account's current database roles. Logout is idempotent and revokes that
session's refresh capability. Existing access JWTs remain valid until their
15-minute expiry, subject to the existing per-request account/role checks.

Run the secured H2 demo (no PostgreSQL or external signing key required):

```sh
APP_ADMIN_PASSWORD=local-demo-password-123 ./mvnw spring-boot:test-run \
  -Dspring-boot.run.profiles=prod,test,api-demo
```

In IntelliJ, create a **Maven** run configuration with command
`spring-boot:test-run -Dspring-boot.run.profiles=prod,test,api-demo` and environment
variable `APP_ADMIN_PASSWORD=local-demo-password-123`. The test classpath supplies
H2 and a demonstration signing key. With normal PostgreSQL production startup,
supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` as described in
[SECURITY.md](SECURITY.md).

Try rotation and replay detection in a second terminal:

```sh
BASE=http://localhost:8080
PAIR=$(curl -fsS "$BASE/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"local-demo-password-123"}')
OLD_REFRESH=$(printf '%s' "$PAIR" | python3 -c 'import json,sys; print(json.load(sys.stdin)["refreshToken"])')

# 200: new access and refresh tokens
PAIR=$(curl -fsS "$BASE/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$OLD_REFRESH\"}")
ACCESS=$(printf '%s' "$PAIR" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
NEW_REFRESH=$(printf '%s' "$PAIR" | python3 -c 'import json,sys; print(json.load(sys.stdin)["refreshToken"])')

# 200: replacement access token authenticates
curl -i "$BASE/api/me" -H "Authorization: Bearer $ACCESS"

# 401: old refresh token replay revokes this session
curl -i "$BASE/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$OLD_REFRESH\"}"

# 401: latest replacement is now revoked too
curl -i "$BASE/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$NEW_REFRESH\"}"
```

To demonstrate logout, log in again and submit that login's refresh token to
`POST /logout` with the same JSON format. Expect 204; subsequent refresh returns
401. Empty/malformed requests return 400. The existing dev security bypass remains
available; login and refresh still validate their supplied credentials/tokens.

## Keycloak OIDC mode

Start Docker Desktop, then run from the project root:

```sh
docker compose -f compose.keycloak.yaml -p javalab4-oidc up -d
```

Wait for `Realm 'java-lab' imported` / Keycloak startup in:

```sh
docker compose -f compose.keycloak.yaml -p javalab4-oidc logs -f keycloak
```

The pinned demo image is Keycloak 26.8.2. It listens only on local port 8180 and
imports `keycloak/java-lab-realm.json`. Its development server and included
passwords are for the local homework demonstration.

Stop the local-mode API before running the following on port 8080:

```sh
./mvnw spring-boot:test-run -Dspring-boot.run.profiles=keycloak,test,api-demo
```

For IntelliJ, use that same command in a **Maven** run configuration. No
`APP_ADMIN_PASSWORD` or `JWT_SECRET` is needed in this mode. For PostgreSQL,
use `spring-boot:run -Dspring-boot.run.profiles=prod,keycloak` with the `DB_*`
variables. `KEYCLOAK_ISSUER` defaults to
`http://localhost:8180/realms/java-lab`; set it to the trusted realm URL for an
external installation. Production Keycloak should use HTTPS and deployment-specific
clients, redirects, users, and credentials.

Open <http://localhost:8080/swagger-ui/index.html>, select the default API
definition if needed, and click **Authorize**. Select `openid`, `profile`, and
`email`, keep client ID `routing-api-docs`, leave client secret empty, and authorize.
Keycloak's login page opens. Sign in with any of these accounts:

| Username | Password | Application role |
| --- | --- | --- |
| `prof-admin` | `keycloak-demo-password-123` | ADMIN |
| `prof-customer` | `keycloak-demo-password-123` | CUSTOMER |
| `prof-driver` | `keycloak-demo-password-123` | DRIVER |

Swagger is the public OIDC client. It runs Authorization Code with S256 PKCE and
requests `openid`, so Keycloak issues access, refresh, and ID tokens. No password
grant or implicit flow is enabled. The API is a stateless resource server; it
accepts access tokens in Bearer headers and does not create browser login sessions.
The ID token describes the login and must not be sent as an API access token.

The demo client has the `basic` scope (stable subject), `roles`, `profile`, and
`email`, plus an audience mapper that adds `routing-api` to access tokens only.
The API validates RSA signatures from Keycloak's JWKS, issuer, expiry, subject,
Bearer token type, and the `routing-api` audience. Local HMAC JWTs, ID tokens,
refresh tokens, and tokens for other APIs are rejected.

`KeycloakRoleConverter` maps `realm_access.roles` and roles under
`resource_access.routing-api.roles` to Spring's `ROLE_*` authorities. Only exact
`ADMIN`, `CUSTOMER`, and `DRIVER` values are accepted. Roles belonging to unrelated
clients are ignored. Existing `@PreAuthorize` rules work with those authorities.
The stable issuer/subject identifies the external user; local accounts are not
implicitly linked by an editable username or email address.

Demonstrate in Swagger:

| Request | ADMIN | CUSTOMER | DRIVER |
| --- | --- | --- | --- |
| GET `/api/me` | 200 + roles | 200 + roles | 200 + roles |
| GET `/api/v1/vehicles` | 200 | 200 | 200 |
| GET `/api/v1/customers` | 200 | 403 | 403 |
| GET `/api/v1/deliveries` | 200 | 403 | 200 |
| POST `/api/v1/scenarios` | 201 | 403 | 403 |

For the scenario body, use
`{"name":"OIDC demo","vehicleIds":[],"deliveryIds":[]}`.
Use a private browser window to switch Keycloak identities, since Keycloak has
its own SSO cookie. Anonymous private requests return 401.

In Keycloak mode the local `/login`, `/register`, `/refresh`, `/logout`, and driver
registration endpoints are absent. Manage identities in Keycloak. `/api/me`
returns `subject`, `username`, `issuer`, and mapped `roles`. Role changes become
visible when a new IdP access token is issued; existing JWTs carry their previous
roles until expiry (five minutes in the demo).

## Keycloak refresh and logout

The IdP owns refresh tokens in this mode. Use the returned `refresh_token` at
Keycloak's token endpoint; replace it after every successful refresh:

```sh
curl -i -X POST http://localhost:8180/realms/java-lab/protocol/openid-connect/token \
  -d grant_type=refresh_token \
  -d client_id=routing-api-docs \
  --data-urlencode "refresh_token=$KEYCLOAK_REFRESH_TOKEN"
```

The realm enables refresh-token rotation with zero permitted reuse. A reused
refresh token returns 400. Logout uses Keycloak's endpoint:

```sh
curl -i -X POST http://localhost:8180/realms/java-lab/protocol/openid-connect/logout \
  -d client_id=routing-api-docs \
  --data-urlencode "refresh_token=$KEYCLOAK_REFRESH_TOKEN"
```

Expect 204 and a rejected subsequent refresh. Already issued access JWTs can
remain valid until expiry. The supplied smoke script obtains the tokens through
the real authorization-code flow and verifies refresh/logout automatically;
it does not print tokens. Swagger's login integration is for interactive API
requests; use the script below to demonstrate refresh rotation explicitly.

## Verification and cleanup

```sh
./mvnw test
# With Keycloak and the API in keycloak mode running:
python3 scripts/test-keycloak.py
# Optional different API port (the registered OIDC callback remains localhost:8080):
python3 scripts/test-keycloak.py http://localhost:18085
```

The standard-library Python script drives the supplied demo login form with
Authorization Code + PKCE, checks state and nonce, obtains all three token types,
checks all roles against the real API, and verifies refresh replay rejection and
logout. It handles localhost Secure cookies the same way a browser does. It is
a smoke test for this fixture, not a general-purpose OIDC client library.

The Java suite checks hashed storage, rotation, absolute expiry, disabled users,
logout, replay-family revocation, concurrent rotation, role changes, and API
rejection of refresh tokens. Signed RSA integration tests cover Keycloak roles,
issuer/audience/signature/expiry validation, and rejection of ID tokens. The
optional PostgreSQL migration test still requires `MIGRATION_TEST_DB_*` variables.

Stop the application with IntelliJ's Stop button, and stop the disposable IdP:

```sh
docker compose -f compose.keycloak.yaml -p javalab4-oidc down
```

There is no persistent Keycloak volume. Recreating the container resets demo
accounts and reimports the realm; it also changes signing keys and invalidates
previous IdP tokens. To apply fixture changes to an already-created demo container,
use `up -d --force-recreate`.

References: [Keycloak OIDC endpoints](https://www.keycloak.org/securing-apps/oidc-layers),
[Keycloak container imports](https://www.keycloak.org/server/containers),
[Keycloak subject and client scopes](https://www.keycloak.org/docs/26.3.5/server_admin/),
and [Swagger OAuth2/PKCE configuration](https://swagger.io/docs/open-source-tools/swagger-ui/usage/oauth2/).
