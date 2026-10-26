# Advanced lab — implementation and walkthrough

## Step 1: Populate the database with DataFaker

`PopulationService` uses DataFaker for names, addresses, and delivery notes. It creates related records in dependency order: depots → drivers/vehicles → customers → deliveries. Generated email addresses include a run UUID and index to satisfy uniqueness even when population runs twice. Coordinates cover a sample region (latitude 44–45, longitude 25–27); they are synthetic and are not geocoded from the fictional addresses. Quantities are positive.

Default population adds **5 depots, 20 drivers, 20 vehicles, 1,000 customers, and 10,000 deliveries**. JDBC inserts retrieve generated IDs; delivery inserts use batches of 500. This avoids keeping thousands of JPA entities in memory and the insert-batching limitation of IDENTITY generation. The operation is transactional: failure rolls back the dataset. The random seed is fixed at 42 by default; UUIDs and generated database IDs intentionally differ between runs.

After running `schema.sql` and setting your database environment variables, in IntelliJ:

1. Open the `DemoApplication` run configuration.
2. Set **Active profiles** to `seed`. Keep the database credentials in **Environment variables**.
3. Optionally set **Program arguments** to `--app.seed.customers=2000 --app.seed.deliveries=20000`.
4. Run the application and look for `Appended DataFaker dataset`.
5. Stop the application and remove the `seed` profile before normal use.

Each explicit seed run **appends another dataset**; it does not delete or replace records. The ordinary customer demonstration is disabled during seeding and benchmarking.

Equivalent shell command (database variables must be exported in that shell):

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed -Dspring-boot.run.arguments="--app.seed.customers=1000 --app.seed.deliveries=10000"
```

DataFaker is pinned in `pom.xml`; see its [official getting-started documentation](https://www.datafaker.net/documentation/getting-started/) for the library's role and providers.

## Step 2: Dynamic search with the Criteria API

`DeliveryFilter` is an immutable Java record. Every search restriction is optional: `null` means ignore that restriction. It supports:

- Customer ID.
- Minimum/maximum latitude and longitude of the customer (an inclusive rectangular area).
- A strictly greater-than quantity threshold.
- A calendar date, interpreted in a specified timezone (default `Europe/Bucharest`).

`DeliverySearchRepository` builds the query using `EntityManager`, `CriteriaBuilder`, and a list of `Predicate` objects. Each supplied filter contributes a predicate. The final query combines them using AND. No filters means all deliveries, subject to pagination.

The customer join lets the query use customer coordinates and name. For dates, it compares timestamps against `[start of day, start of next day)`. Computing both boundaries in the timezone correctly handles 23-hour and 25-hour daylight-saving days.

Call the Spring-injected `DeliverySearchService` from a runner or another bean:

```java
var filter = new DeliveryFilter(
        null,                         // any customer; or supply a real customer ID
        new BigDecimal("44"),         // minimum latitude
        new BigDecimal("45"),         // maximum latitude
        new BigDecimal("25"),         // minimum longitude
        new BigDecimal("27"),         // maximum longitude
        new BigDecimal("100"),        // quantity strictly above 100
        LocalDate.of(2026, 10, 25),    // null to ignore the date
        ZoneId.of("Europe/Bucharest")
);
var results = deliverySearchService.search(filter, 0, 100);
```

Page numbers start at zero. Page size must be 1–200. Results have a deterministic ID ordering, and an empty list means no matches on that page. There is no additional total-count query. `DeliveryFilter.all()` creates an unrestricted filter.

The Criteria query constructs `DeliveryView` records directly, containing delivery ID, customer ID/name, quantity, and timestamp. The list and records are immutable, so caching does not expose mutable JPA entities or lazy collections. These methods are service APIs; this lab does not add HTTP endpoints.

## Step 3: Spring Cache

`CacheConfiguration` enables Spring caching with a Caffeine cache named `deliverySearches`. It holds at most 500 result pages and expires entries five minutes after writing them.

`DeliverySearchService.search` has `@Cacheable`. Spring's default cache key contains every method argument: filter, page, and size. A first call runs the Criteria query and stores its result; a repeated equal call returns the cached list without executing SQL. `searchUncached` executes the identical query without caching, providing the benchmark baseline.

Always call the injected service. Calling an annotated method via `this` inside the same class bypasses Spring's proxy. See [Spring's caching guide](https://spring.io/guides/gs/caching/) for how `@Cacheable` interception works.

`CustomerService.save`, `DeliveryService.save`, and the population operation evict all delivery-search pages. Customer changes matter because names and coordinates appear in search results and conditions. A transaction-aware cache manager defers changes until commit: failed transactions do not evict committed entries. The cache tests verify both commit and rollback behavior.

This is a local, single-application cache, intended for repeated reads. Writes must go through these services; direct SQL or repository writes bypass their eviction annotations. Independent application instances do not share invalidations. Expiration limits stale data, but this design is not a strict consistency guarantee for concurrent readers/writers.

## Step 4: Measure with JMH

JMH is enabled only by the Maven `benchmark` profile. The source is `src/jmh/java/com/example/demo/benchmark/DeliverySearchBenchmark.java`. The Python launcher builds into `target/benchmark-build` to isolate the annotation-generated harness from normal IDE builds and starts Java with a full classpath, allowing JMH's forked JVMs to load the application.

Run from the project directory (Python 3 and Java 21+ required):

```sh
python3 scripts/benchmark.py
```

By default, each benchmark trial uses a disposable H2 database containing 1,000 customers and 10,000 deliveries. It never populates your PostgreSQL database. The benchmark compares one repeated query for a 100-row page, filtered by quantity above 100:

- `uncached`: query, database access, and DTO construction on every call.
- `cached`: a warmed Spring Cache hit for the same request.

Configuration:

- Average execution time, in microseconds per operation; lower is better.
- 5 warm-up iterations of 2 seconds, excluded from results.
- 5 measurement iterations of 2 seconds.
- 2 separate JVM forks per benchmark method, 1 thread.
- Spring startup, data generation, and cache priming occur in `@Setup(Level.Trial)`, outside the measured method.
- Methods return their results so JMH consumes them and prevents dead-code elimination.
- Benchmark SQL logging and Hibernate statistics are disabled.

Warm-up lets the JVM optimize hot code; separate forks reduce interference from an earlier run's JIT decisions. JMH reports mean, error, and raw measurements. These are starting settings: increase warm-up/measurement lengths when investigating variability. See the [OpenJDK JMH project](https://github.com/openjdk/jmh) for its harness and benchmark examples.

Results are printed to the console and saved to `target/jmh-results.json`. Calculate:

```text
speedup = uncached average / cached average
latency reduction (%) = (1 - cached average / uncached average) * 100
```

This measures the benefit for a warm, repeatedly requested page. It does not model cache misses, concurrent writes, HTTP latency, or an entire workload. H2 measurements are not PostgreSQL measurements. Do not present a short smoke run or this cache-hit comparison as a general system-wide speedup.

To benchmark PostgreSQL after seeding it, export `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in your terminal and run:

```sh
python3 scripts/benchmark.py -p database=postgres
```

This mode validates your existing schema and requires at least 10,000 deliveries. It does not generate or delete data. Keep other database activity low for a meaningful comparison. IntelliJ environment variables do not automatically apply to a separate terminal.

## Step 5: Verify correctness

```sh
./mvnw test
```

The advanced tests verify optional filters individually and in combination, pagination, the Bucharest daylight-saving boundary, invalid input, cache hits avoiding SQL, immutable results, eviction after delivery and customer changes, rollback behavior, and repeatable additive DataFaker population. They use a separate H2 database and no Mockito mocks.

## Measured result — October 26, 2026

Executed locally with JMH 1.37, Java 25, H2, 1,000 customers, and 10,000 deliveries. Two forks; five 2-second warm-up iterations and five 2-second measurement iterations per fork. Each call returns the same 100-row page.

| Method | Mean ± JMH error (99.9% confidence interval), µs/op |
| --- | ---: |
| Warm cache | 0.317 ± 0.021 |
| Uncached | 36.749 ± 1.204 |

The ratio of these means is **116.0×**, or **99.14% lower latency**, for this repeated warm-cache query. This is not a PostgreSQL or end-to-end application result. An initial shorter run showed decreasing uncached measurement times; extending warm-up removed that strong trend.

Raw scores, fork measurements, and configuration are preserved in [benchmark-results/h2-results.json](benchmark-results/h2-results.json). New benchmark runs write `target/jmh-results.json`; they do not overwrite this recorded result.
