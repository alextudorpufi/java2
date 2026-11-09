# Advanced REST: migrations, hypermedia, and API versions

## Flyway owns the database schema

Spring Boot runs Flyway before constructing the JPA entity manager. Hibernate uses `ddl-auto: validate` to check the resulting schema; it no longer creates test tables. Both PostgreSQL and H2 tests run the same versioned SQL migrations:

| Migration | Changes |
| --- | --- |
| `db/migration/V1__create_routing_schema.sql` | Depots, drivers, customers, vehicles, deliveries, checks, foreign keys, indexes |
| `db/migration/V2__add_scenarios.sql` | Scenarios and both many-to-many join tables with indexes |

Flyway records applied versions and checksums in `flyway_schema_history`. Subsequent starts validate the history and apply only pending migrations. SQL initialization is disabled, and Flyway clean is disabled. The old `schema.sql` and `schema-scenarios.sql` files are historical references; do not execute them when using Flyway.

For a **new empty database**, create the database itself, configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, and start the application normally:

```sh
./mvnw spring-boot:run
```

Flyway creates all application tables automatically.

### Adopt an existing manually created database

The default `baseline-on-migrate: false` deliberately rejects an untracked, nonempty schema. Baseline only after checking which earlier scripts have actually been applied. Back up the existing database first.

| Existing schema | One-time baseline version | What happens on startup |
| --- | --- | --- |
| Original five Lab 4 tables, without scenario tables | `1` | Existing tables/data remain; Flyway executes V2 |
| All eight homework tables, including both scenario join tables | `2` | Existing tables/data remain; both initial migrations are treated as already applied |
| Empty database | No baseline | Flyway executes V1 and V2 |

Compare the existing tables, columns, constraints, and indexes to the migration files. A baseline asserts that the preceding schema changes already exist; it does not validate their SQL or create missing objects. Hibernate subsequently checks entity mappings, but does not verify every check constraint or index. A partial or divergent schema must be reconciled before baselining.

For the original Lab 4 schema, run once with these environment variables:

```sh
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
SPRING_FLYWAY_BASELINE_VERSION=1 \
./mvnw spring-boot:run
```

For the complete manually created homework schema, use `SPRING_FLYWAY_BASELINE_VERSION=2` instead. In IntelliJ, set these same variables in the run configuration for the first adoption run. After the application starts successfully, remove both baseline variables and use the normal run command. Do not baseline a database that already has Flyway history.

Verify the history in pgAdmin:

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

For future changes, add `V3__description.sql`, then V4, and so on. Never edit a migration already applied to a shared database: its checksum is part of the migration history. Prefer forward corrections in a new migration. Test upgrades on a copy of the current database; recover destructive mistakes from a backup instead of deleting migration history or enabling `clean`.

## Customer HATEOAS links

Customer create, list, read, and update responses retain their scalar fields and add `_links`:

```json
{
  "id": 7,
  "name": "Ana",
  "email": "ana@example.com",
  "phone": "0700000000",
  "address": "Bucharest",
  "latitude": 44.4268,
  "longitude": 26.1025,
  "_links": {
    "self": {"href": "http://localhost:8080/api/v1/customers/7"},
    "collection": {"href": "http://localhost:8080/api/v1/customers"},
    "deliveries": {"href": "http://localhost:8080/api/v1/customers/7/deliveries"}
  }
}
```

A client can follow these relationships without constructing a URL or knowing which query parameters filter deliveries. The links use the current request's scheme, host, port, and application context path, with the canonical v1 API prefix. They are also included in XML representations. The API uses ordinary JSON/XML DTOs with link relations; it does not advertise a HAL media type.

`GET /api/v1/customers/{id}/deliveries` returns only that customer's deliveries, ordered by ID, without needing a time interval. An existing customer with no deliveries returns `[]`; a nonexistent customer returns `404`. This endpoint supports XML and the existing ETag / `If-None-Match` behavior. The legacy `/api/customers/{id}/deliveries` alias also works.

## URI-based versioning

The explicit current API contract is **v1**:

| Resource | Canonical base path | Compatibility alias |
| --- | --- | --- |
| Customers | `/api/v1/customers` | `/api/customers` |
| Vehicles | `/api/v1/vehicles` | `/api/vehicles` |
| Deliveries | `/api/v1/deliveries` | `/api/deliveries` |
| Scenarios | `/api/v1/scenarios` | `/api/scenarios` |

Each base path supports the existing POST, GET list, GET by ID, PUT, and DELETE operations. Versioning also applies to the customer deliveries subresource. There is no implemented v2; requesting `/api/v2/...` returns `404`.

Unversioned routes remain functional aliases and include a `Link` response header pointing to the same versioned resource with `rel="successor-version"`. Customer body links always point to v1. Create responses keep `Location` in the namespace used for the request, preserving older clients' behavior. Collections remain JSON arrays, and `_links` is an additive customer field. Clients must ignore unknown response properties when using this compatible extension.

Swagger UI offers `v1` and `legacy` groups. Their OpenAPI documents are `/v3/api-docs/v1` and `/v3/api-docs/legacy`; `/v3/api-docs` contains all routes.

### Transition strategy

1. **Move existing clients to v1 now.** Change `/api/customers` to `/api/v1/customers`, and likewise for other resources. Follow canonical links returned by the server. The request fields and status codes remain the same. Refresh cached responses after switching URLs.
2. **Keep v1 stable.** Add optional fields and new resources compatibly. Breaking changes, such as renaming/removing fields or changing collection structure, require a new major URI version.
3. **Introduce v2 alongside v1 when needed.** Add separate v2 controllers, DTOs, response/link mappings, and an OpenAPI group. Reuse shared business logic. Keep links within each version. Maintain contract tests for both versions.
4. **Evolve the shared database in stages.** Add new columns/tables with Flyway while v1 continues working, migrate existing rows, and switch v2 to the new representation. Remove old columns only after v1 is retired. Database migration numbers and API versions are independent.
5. **Announce retirement before removal.** Publish the v1-to-v2 field/status mapping and examples; mark v1 deprecated in OpenAPI; announce a support window and add `Deprecation`, `Sunset`, and successor links when actual dates and the replacement API exist. No retirement date is currently scheduled for the legacy aliases or v1.
6. **Verify migration, then retire.** Check client adoption and regression tests before removing old routes in an announced release. Keep migration history permanently; do not rewrite old SQL to match the latest API.

## Verification

`./mvnw test` runs the earlier tests plus migrations, legacy baselining at both versions, refusal of untracked schemas without opt-in, checksum validation, versioned CRUD, version-specific OpenAPI documents, followable customer links, XML, and conditional delivery requests.

The optional real PostgreSQL test creates two randomly named schemas in a test database and drops them afterward. Use a disposable database and set `MIGRATION_TEST_DB_URL`, `MIGRATION_TEST_DB_USERNAME`, and `MIGRATION_TEST_DB_PASSWORD` before running `./mvnw test`. Without these variables, that one PostgreSQL-specific test is skipped; all H2 tests still run the migration SQL.

For a Flyway-managed disposable H2 API server with a demo depot:

```sh
./mvnw spring-boot:test-run \
  -Dspring-boot.run.profiles=test,api-demo \
  -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false \
  -Dspring-boot.run.arguments=--server.port=18080
```

Then test canonical v1 routes and the compatibility aliases:

```sh
python3 scripts/test-customers.py http://localhost:18080
python3 scripts/test-routing-api.py http://localhost:18080 1
python3 scripts/test-customers.py http://localhost:18080 /api
python3 scripts/test-routing-api.py http://localhost:18080 1 /api
```

The routing script follows a customer's deliveries link, checks the legacy successor link and rejection of v2, and exercises CRUD, ETags, XML, and both OpenAPI routes. The `api-demo` profile's fixture runner lives exclusively in test sources and does not ship in the production application.
