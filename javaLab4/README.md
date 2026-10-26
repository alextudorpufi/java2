# Vehicle Routing Optimization System — Lab 4

## Run locally

1. Create a PostgreSQL database and run `src/main/resources/schema.sql` once in pgAdmin's Query Tool.
2. Set `DB_URL` (for example `jdbc:postgresql://localhost:5432/javaLab4`), `DB_USERNAME`, and `DB_PASSWORD` in the application run configuration's **Environment variables** field. Use your actual port and database name. Do not commit passwords.
3. Run `DemoApplication` in IntelliJ, or run `./mvnw spring-boot:run` in a shell with those variables set. Java 21 or newer is required.
4. Look for `Customer repository test passed` in the log. The runner creates a demo customer on its first run and reuses it on later runs.

Hibernate uses `ddl-auto: validate` for the application: the SQL script creates the schema, and Hibernate checks the entity mappings. Do not rerun the creation script against existing tables. The homework mappings require no changes to the compulsory schema.

## 1. Entities and associations

All entities are in `com.example.demo.entity`. Each persistent entity maps to its existing table and uses a generated `Long` ID. Decimal coordinates and quantities use `BigDecimal`; delivery timestamps use `OffsetDateTime` to match PostgreSQL's timestamp with time zone.

| Association | Owning side (writes the foreign key) | Inverse side |
| --- | --- | --- |
| Depot to vehicles | `Vehicle.depot`: `@ManyToOne`, `depot_id` | `Depot.vehicles`: `@OneToMany(mappedBy = "depot")` |
| Driver to vehicle | `Vehicle.driver`: `@OneToOne`, nullable unique `driver_id` | `Driver.vehicle`: `@OneToOne(mappedBy = "driver")` |
| Customer to deliveries | `Delivery.customer`: `@ManyToOne`, `customer_id` | `Customer.deliveries`: `@OneToMany(mappedBy = "customer")` |

`mappedBy` is the Java field on the owning entity, not a database column. A vehicle must have a depot, and a delivery must have a customer. A vehicle can have no driver. The unique foreign key enforces one vehicle per assigned driver, including when writes bypass Java.

Use `Vehicle.setDepot`, `Vehicle.setDriver`, and `Delivery.setCustomer` to change associations. These methods update both sides in memory. Collection getters return unmodifiable views so callers cannot accidentally change only the inverse side. Associations do not cascade deletes: a vehicle should not delete its depot or driver. Save referenced entities before saving vehicles or deliveries.

The vehicle's depot is its home depot. Future routing logic must use this depot at both the start and end of a route; this lab does not implement routes.

## 2. Driver/Customer inheritance

`Person` is an abstract `@MappedSuperclass` containing the shared ID, name, email, and phone mappings. `Driver` and `Customer` inherit these fields; their own tables still contain the columns. There is no `persons` table or `PersonRepository`, and IDs are generated separately per concrete table.

This is useful because both classes represent people with identical contact fields. Full entity inheritance would add an unnecessary shared table or discriminator to the given schema. Separate classes would also be reasonable if customer contact data later differs substantially (for example, company customers).

Lombok generates ordinary getters, scalar setters, no-argument constructors, and service constructors. Associations use explicit methods. Avoid `@Data` on these entities: generated equality and string methods could traverse bidirectional relationships.

## 3. Repositories

Each of the five entities has a `JpaRepository<Entity, Long>`. Spring provides implementations for `save`, `findById`, `findAll`, and other standard operations; a handwritten implementation class is unnecessary.

## 4. Three query types

- **Derived query:** `VehicleRepository.findByDepotIdAndAvailableTrue` finds available vehicles at a depot. Spring derives the query from the method name. `CustomerRepository.findByEmail` is another example.
- **Explicit JPQL:** `DeliveryRepository.findForCustomerInPeriod` selects deliveries for a customer in `[from, to)`, ordered by timestamp. It includes the start and excludes the end to avoid counting boundary deliveries twice. JPQL uses `Delivery`, `customer.id`, and `timestamp`, rather than SQL table and column names.
- **Transactional modifying query:** `VehicleRepository.updateAvailability` runs a JPQL update with `@Modifying` and `@Transactional` and returns the number of updated rows. It flushes pending writes and clears the persistence context because bulk updates bypass managed entity state. Previously loaded objects are detached afterward; reload them before further changes.

The assignment requires at least one of each query type across the repositories, not all three in every repository.

## 5. Services

`CustomerService`, `DepotService`, `DriverService`, `VehicleService`, and `DeliveryService` each use their corresponding repository. `@Service` registers them with Spring, and Lombok's `@RequiredArgsConstructor` supplies constructor injection for their final dependencies.

Every service provides `save`, `findById`, and `findAll`. Reads use `@Transactional(readOnly = true)`; writes override this with `@Transactional`. Missing IDs produce an `IllegalArgumentException`.

`VehicleService` also lists available vehicles, changes availability, assigns a driver, and unassigns a driver. Assignment rejects an occupied vehicle or a driver assigned elsewhere. Unassign first to move an existing driver. The unique database constraint remains the final safeguard against concurrent conflicting assignments. These operations use JPA dirty checking: changes to managed entities are saved at commit without another explicit `save` call.

`DeliveryService` validates the date interval before calling the JPQL query. `CustomerDemoRunner` now uses `CustomerService`, demonstrating the flow runner → service → repository → database.

Collections are lazy. Access them inside a transaction (as the integration tests do); future API operations should assemble response data inside service transactions rather than directly serialize bidirectional entities.

## Verification

Run `./mvnw test`. The `test` profile uses a disposable H2 database in PostgreSQL compatibility mode, with Hibernate creating and dropping the test schema. No local database credentials are required, and these tests do not touch your PostgreSQL database.

The integration tests check persistence/reloading of relationships, both sides of the driver assignment, rejection of assignment conflicts, moving a driver after unassignment, database enforcement of driver uniqueness, the derived query, JPQL interval boundaries and customer filtering, and the modifying query's persistence-context clearing.

H2 is only a test dependency. These tests verify JPA behavior, but do not execute the PostgreSQL SQL script or verify its PostgreSQL-specific checks. Start the application against your local PostgreSQL database to validate the real schema too.

## Advanced section

See [ADVANCED.md](ADVANCED.md) for DataFaker population, Criteria API search, Spring caching, and the JMH benchmark, including step-by-step run commands.
