# Security homework: database users, JWT, and profiles

This replaces the compulsory mock login and HTTP Basic implementation.
The access/refresh-token extension and Keycloak OIDC setup are documented in
[SECURITY-ADVANCED.md](SECURITY-ADVANCED.md).

## Domain and database

`AppUser` stores a unique username, BCrypt password hash, enabled flag, and roles
(`ADMIN`, `CUSTOMER`, `DRIVER`) in `app_users` and `user_roles`. Every `Customer`
and `Driver` has a required, unique one-to-one account inherited from `Person`.
An administrator can exist without a customer or driver profile. Login usernames
are separate from editable contact email addresses.

Flyway migration `V3__add_user_accounts.sql` preserves existing customer/driver
IDs and relationships and links them to disabled accounts with the appropriate
role. The `!` password marker cannot authenticate. Likewise, profiles created
through the legacy CRUD API receive disabled accounts; credential registration
is the flow that creates usable credentials. No shared password is assigned to
existing records. Account claiming/password reset is outside this assignment.
Deleting a customer or driver through JPA also deletes its account and roles.
Passwords and account entities are never included in profile response DTOs.

## Authentication and registration

- `POST /register`: public customer registration, returns 201 with account ID,
  username, and roles. The request contains `username`, `password`, and a nested
  `customer` object matching `CustomerRequest`. Public registration always assigns
  `CUSTOMER`; supplied role fields cannot grant privileges.
- `POST /login`: validates database credentials and returns `accessToken`,
  `tokenType: Bearer`, `expiresIn: 900`, `refreshToken`, and `refreshExpiresIn`. Invalid credentials or disabled users
  receive the same 401 response.
- `POST /refresh`: exchanges a single-use refresh token for a new access/refresh
  pair. Reusing an old token revokes that login's refresh session.
- `POST /logout`: revokes the refresh session identified by the supplied token.
- `POST /api/users/drivers`: ADMIN-only driver registration, accepting username,
  password, name, email, and phone. Returns an account with `DRIVER` role.
- `GET /api/me`: returns the authenticated account's ID, username, and roles.

Registration requires a 3–50 character lowercase username and a password of at
least 12 characters, no more than 72 UTF-8 bytes. BCrypt salts each password.
Duplicate usernames or contact emails return 409, invalid inputs return 400,
and account/profile creation is one transaction.

JWTs use HS256 with a Base64-encoded secret containing at least 32 random bytes.
Signature, issuer, and expiry are verified by Spring Security's resource server.
Tokens expire after 15 minutes (with the decoder's standard clock-skew allowance).
For each valid token, current roles and enabled status are loaded from the
database, so disabling an account or changing its roles takes effect without
waiting for expiry. The JWT subject is the immutable login username.

Requests use `Authorization: Bearer <token>`. HTTP Basic and form login are
disabled. Authentication is stateless, with no session or authentication cookies;
CSRF is disabled for this header-only API. Refresh tokens are random opaque
values, stored only as SHA-256 hashes, with a seven-day absolute session lifetime.
Rotation is serialized by a database row lock. Logout/replay revokes refresh
capability; already issued access tokens remain valid until expiry unless the
account is disabled. Use HTTPS for deployment.

## Access policy

The same rules cover `/api/...` compatibility aliases and `/api/v1/...` routes.

| Route / operation | Access |
| --- | --- |
| POST `/register`, `/login`, `/refresh`, `/logout` | Public; refresh/logout require possession of a refresh token |
| GET vehicle list/details, Swagger UI, OpenAPI | Public |
| GET `/api/me` | Any authenticated account |
| GET deliveries | ADMIN or DRIVER |
| GET customers, customer delivery links, scenarios, Actuator | ADMIN |
| POST / PUT / PATCH / DELETE except the four authentication endpoints | ADMIN |
| Other routes | ADMIN |

Customer contact details and delivery information are not public. Drivers have
read access to deliveries for this lab; customers can inspect their own account
through `/api/me`. No customer or driver may edit arbitrary shared records.

`@PreAuthorize("hasRole('ADMIN')")` protects controller mutations, CRUD service
create/update/delete methods, and driver registration. Service-level checks are
exercised directly in tests, independently of the HTTP filter chain. Internal
`save` helpers remain available to the existing fixture/population routines.

## Profiles and running

`prod` is the default profile. Security is also enabled with other profiles unless
`dev` is explicitly active. `dev` bypasses HTTP authorization and does not enable
method-security interceptors. If both `dev` and `prod` are selected, production
authorization wins. Login still validates credentials, and `/api/me` needs an
identity even in dev; ordinary CRUD operations need no credentials in dev.

Production requires `JWT_SECRET`; there is no production fallback key. Supply the
existing `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` settings for PostgreSQL:

```sh
export JWT_SECRET=$(openssl rand -base64 32)
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD='replace-with-a-long-local-password'
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

`APP_ADMIN_PASSWORD` optionally provisions the first administrator with BCrypt.
It does not reset an existing account or promote an existing non-admin username.
Remove it from the environment after provisioning. Keep the same JWT signing key
across restarts; changing it invalidates outstanding tokens. Demo customer data is
only created in `dev`/`test`, not normal production startup.

For a disposable H2 demo with security enabled (test-only key):

```sh
export APP_ADMIN_PASSWORD='local-demo-password-123'
./mvnw spring-boot:test-run -Dspring-boot.run.profiles=prod,test,api-demo
```

For a disposable H2 demo with the required dev bypass:

```sh
./mvnw spring-boot:test-run -Dspring-boot.run.profiles=dev,test,api-demo
```

The dev profile generates an ephemeral signing key when none is configured; it
is never used when `prod` is active, regardless of profile order.
The `test` profile and H2 dependency exist only on the test classpath.

## Try the flow

With the secured H2 demo running:

```sh
curl -i http://localhost:8080/api/v1/vehicles       # 200 public read
curl -i http://localhost:8080/api/v1/customers      # 401 private data

curl -i -H 'Content-Type: application/json' \
  -d '{"username":"ana","password":"customer-password-123","customer":{"name":"Ana","email":"ana@example.com","phone":"0700000000","address":"Bucharest","latitude":44,"longitude":26}}' \
  http://localhost:8080/register

TOKEN=$(curl -fsS -H 'Content-Type: application/json' \
  -d '{"username":"ana","password":"customer-password-123"}' \
  http://localhost:8080/login | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me
# Customer write: 403
curl -i -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/vehicles/1
```

The existing curl smoke scripts obtain an ADMIN JWT automatically:

```sh
export API_USERNAME=admin
export API_PASSWORD='local-demo-password-123'
python3 scripts/test-customers.py
python3 scripts/test-routing-api.py
```

Against a dev server, use `API_DEV_MODE=true` instead. Swagger's **Authorize**
button accepts the JWT from `/login` without the `Bearer` prefix.

## Verification

Run `./mvnw test`. Tests cover registration and BCrypt matching, duplicate and
invalid inputs, rollback, customer/driver accounts, successful and failed login,
expired/tampered/wrong-issuer JWTs, disabled users, database role changes, public
reads, both API versions' protected writes, direct method authorization, dev
bypass, mixed-profile safety, and preservation of legacy data through Flyway.
The existing CRUD suite still runs with ADMIN authority. The optional PostgreSQL
migration test requires the documented `MIGRATION_TEST_DB_*` environment variables.

References: [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html),
[method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html),
and [password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
