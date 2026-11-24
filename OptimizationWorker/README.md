# OptimizationWorker — compulsory homework

The two applications communicate asynchronously through Kafka. The VRP API is in
`../javaLab4`; this project is the independent worker. Both require Java 21+.

## Component flow

```mermaid
sequenceDiagram
    participant Client
    participant API as VRP API
    participant DB as API database
    participant Kafka
    participant Worker as OptimizationWorker
    Client->>API: POST /api/v1/scenarios/{id}/optimize
    API->>DB: Commit job UUID + scenario snapshot (QUEUED)
    API-->>Client: 202 Accepted + Location
    loop Every 2 seconds
        API->>DB: Find unpublished jobs
        API->>Kafka: Publish request; wait for broker confirmation
        API->>DB: Mark published
    end
    Kafka->>Worker: Deliver request
    Worker->>Worker: Build capacity-aware routes
    Worker->>Kafka: Publish COMPLETED / FAILED acknowledgment
    Kafka-->>Worker: Confirm acknowledgment publication
    Worker->>Kafka: Commit consumed request offset
    Kafka->>API: Deliver acknowledgment
    API->>DB: Store terminal status and result
    API->>Kafka: Commit acknowledgment offset
    Client->>API: GET Location
    API-->>Client: Job status
    Client->>API: GET Location/solution
    API-->>Client: Completed routes
```

1. `OptimizationController` accepts an administrator's explicit solve request.
   Scenario create/update remains ordinary CRUD. Each solve creates a new UUID.
2. `OptimizationService` reads the scenario within a database transaction and
   snapshots available vehicles, capacities, depot coordinates, delivery quantities,
   and destination coordinates. The worker never queries the API or its database.
3. `OptimizationJob` doubles as a small transactional outbox: the request JSON and
   QUEUED job are saved together. `OptimizationDispatcher` selects up to 20 pending
   publications every two seconds. `OptimizationPublisher` locks one job, sends it,
   waits for Kafka, then marks it published. Broker failures leave it retryable.
4. Kafka stores the request in `vrp.optimization.requests.v1`. Its key is the job
   UUID; workers share `optimization-workers-v1`, so each partition is assigned to
   one worker in the group. Three partitions allow up to three active consumers.
5. `OptimizationListener` deserializes the request, calls `GreedyVrpSolver`, and
   publishes an acknowledgment to `vrp.optimization.acknowledgments.v1`, with the same
   UUID and scenario ID. It waits for publication before returning. Spring then
   commits the input offset (`ack-mode: record`, auto-commit disabled).
6. The API's acknowledgment listener locks the matching job and saves COMPLETED or
   FAILED plus the result. Repeated terminal acknowledgments have no effect. Unknown
   job IDs are logged and ignored, e.g. old events after resetting a local database.
7. The client polls the returned Location. QUEUED covers both waiting for broker
   publication and waiting for the worker. Once COMPLETED, append `/solution`
   to fetch the result directly.

A Kafka offset acknowledgment means **this record was consumed**. The JSON
acknowledgment is a separate business event meaning **the solve attempt completed
or failed**. Kafka's producer `acks: all` is a third concept: confirmation that the
broker accepted a published record. The single local broker has no replica redundancy.

## Run

Start Kafka here (Docker Desktop must be running):

```sh
docker compose up -d --wait
```

Start the worker in another terminal:

```sh
./mvnw spring-boot:run
```

Start `../javaLab4` with its existing database/authentication configuration:

```sh
cd ../javaLab4
./mvnw spring-boot:run
```

Both default to `localhost:9092`; override `KAFKA_BOOTSTRAP_SERVERS` if needed.
The API retains its existing HTTP port and security. The worker has no web server.
The API applies Flyway V5 to add `optimization_jobs`; no existing tables are changed.

With an existing scenario and administrator access token:

```sh
curl -i -X POST http://localhost:8080/api/v1/scenarios/1/optimize \
  -H "Authorization: Bearer $ACCESS_TOKEN"
# 202; Location: http://localhost:8080/api/v1/optimizations/<requestId>

curl http://localhost:8080/api/v1/optimizations/<requestId> \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl http://localhost:8080/api/v1/optimizations/<requestId>/solution \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

The solution endpoint returns 200 for a completed job, 409 for QUEUED or FAILED,
and 404 for an unknown job. A failed job’s status response includes its failure
message and any partial routes. The legacy `/api` aliases also work. A missing scenario/job returns 404; existing
security rules restrict submitting and polling jobs to administrators.

For a disposable local demonstration without PostgreSQL or authentication, run the
API with its **existing dev profile** and H2 on the test classpath (port 18081):

```sh
cd ../javaLab4
./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.profiles=dev,seed \
  '-Dspring-boot.run.arguments=--server.port=18081 --spring.datasource.url=jdbc:h2:mem:vrp-demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --spring.datasource.username=sa --spring.datasource.password= --spring.datasource.driver-class-name=org.h2.Driver --app.seed.customers=3 --app.seed.deliveries=4'
```

This seeds 20 vehicles and four deliveries in an isolated, in-memory database.
Create a scenario with vehicle IDs `[1,2]` and delivery IDs `[1,2,3,4]`, then submit it:

```sh
curl -H 'Content-Type: application/json' -d \
  '{"name":"Demo","vehicleIds":[1,2],"deliveryIds":[1,2,3,4]}' \
  http://localhost:18081/api/v1/scenarios
curl -X POST http://localhost:18081/api/v1/scenarios/1/optimize
# Copy requestId into GET /api/v1/optimizations/<requestId>
```

An acknowledgment includes `version`, `requestId`, `scenarioId`, `status`, `routes`,
`unassignedDeliveryIds`, and `message`. Each route has `vehicleId`, ordered
`deliveryIds`, exact decimal `load`, and `distanceKm`; it starts and ends at that
vehicle's depot. Empty scenarios complete with no routes. Nonempty scenarios with
no available vehicles fail and list the unassigned deliveries.

## Implementation decisions and limits

- **Kafka:** already selected in the starter project. The official Apache image
  runs a single broker/controller in KRaft mode, so no ZooKeeper is needed. A named
  volume preserves broker data; the published port is bound to localhost.
- **Independent Spring applications:** both use Boot 4.1.1 for consistent dependency
  versions. The worker's initial 4.2.0 parent was aligned with the existing API.
- **Versioned JSON:** records describe a small explicit contract without Java type
  headers, lazy JPA proxies, credentials, or customer personal details. Strings are
  serialized at Kafka's boundary, so Java package names can differ. The two small
  `OptimizationMessages` copies must remain in sync; a shared contract library is a
  sensible later refactoring if they evolve.
- **Snapshot:** later edits/deletes do not change an already submitted solve. Jobs
  deliberately have no scenario foreign key so historical results remain readable.
  Only available vehicles are included; driver assignment is not a solver constraint.
- **At-least-once delivery:** a crash after Kafka accepts a request but before the DB
  marks it published can repeat that request. The worker's calculation is deterministic
  and has no external side effects, and the API ignores duplicate terminal results.
  This is not an exactly-once distributed transaction. A crash after result publication
  but before request-offset commit can likewise repeat an acknowledgment.
- **Routing heuristic:** largest capacity first, then nearest delivery that fits;
  deterministic ID ordering resolves ties. Quantities/capacities use `BigDecimal`.
  Haversine estimates straight-line kilometers and includes return to the depot.
  Each delivery is indivisible, each vehicle makes at most one trip, and no time
  windows, road network, traffic, or global optimum are modeled. A FAILED attempt may
  still have a partial route set; it does not prove mathematical infeasibility.
- **Failure behavior.** The solver reports unassigned deliveries in a normal FAILED
  result. When a Kafka listener throws a retryable exception, Spring retries the
  original record twice, one second apart. If all three deliveries fail, it publishes
  the original key and value plus diagnostic headers to the source topic's `.DLT`.
  Spring may send exceptions it classifies as fatal directly to the dead-letter topic.
  Failed dead-letter publication leaves the source record eligible for redelivery.
  The API uses the original Kafka key to mark a matching QUEUED job FAILED. Dead
  letters with unusable keys remain available for inspection. The API retries
  temporary DB failures while handling a dead letter. Duplicate results and dead
  letters preserve the first recorded terminal state.
  A stopped worker leaves jobs QUEUED. Kafka retention bounds recovery, and each
  environment should use its own broker or topics.
- **Outbox tradeoff:** the DB row remains locked during the bounded broker send. This
  keeps the implementation small and prevents concurrent dispatch of the same job;
  a higher-throughput service would use leases or CDC instead.

## Follow the code in execution order

1. Start in `javaLab4` at `ScenarioController.create` if you need to save a scenario.
   `ScenarioService.create` and `apply` resolve vehicle and delivery IDs and save
   their memberships. The saved scenario ID is the input to the optimization endpoint.
2. Open `OptimizationController.submit` and follow it into `OptimizationService.submit`.
   Look for the creation of the request UUID, the snapshot of available vehicles and
   deliveries, and the `jobs.save` call. `V5__add_optimization_jobs.sql` defines the
   table that stores this job and its pending Kafka publication.
3. Follow `OptimizationDispatcher.dispatch` into `OptimizationPublisher.publish`.
   The dispatcher scans for unpublished jobs. The publisher locks one job,
   sends its JSON with the UUID as the Kafka key, then marks it published after
   Kafka confirms the send.
4. Switch to the worker and open `OptimizationListener.consume`. Its
   `@KafkaListener` is the consumer entry point. Check `OptimizationMessages.Request`
   in both projects to see the JSON fields exchanged over Kafka.
5. Step into `GreedyVrpSolver.solve`. For each vehicle, follow `remaining`, `load`
   and `current`. At each stop, it scans for the closest delivery that fits. It
   removes that delivery, adds its demand and moves to its coordinates. The route
   distance includes the leg back to the depot. If anything remains after the last
   vehicle, the solver returns FAILED with the unassigned IDs.
6. Return to `OptimizationListener.consume` and find the send to the acknowledgment
   topic. The same event contains the outcome and routes. In the API,
   `OptimizationService.acknowledge` consumes it, locks the job, and saves the result.
7. Read `OptimizationController.find` and `solution`. The first reads the job state
   and the second returns the stored result once the state is COMPLETED.
8. Open `KafkaConfiguration.kafkaErrorHandler` in both projects. Follow retries
   through `DefaultErrorHandler` and dead-letter publication through
   `DeadLetterPublishingRecoverer`. Then read `OptimizationService.handleDeadLetter`
   in the API to see how the original message key connects an exhausted record to
   its job.

For example, give a vehicle capacity 10 and put two deliveries along the same
direction from its depot. Demand 4 is 1 km away and demand 6 is 2 km away. The
solver picks the 1 km delivery, then the 2 km delivery, and returns to the depot
with a total assigned load of 10. A third delivery with demand 1 requires another
vehicle and otherwise appears in `unassignedDeliveryIds`.

## Verification

Run `./mvnw test` in both projects. Worker tests cover capacity, decimal arithmetic,
nearest-first ordering, return distance, multiple vehicles, unassigned deliveries,
empty scenarios, and invalid data. API tests cover durable snapshots, excluding
unavailable vehicles, failed publication followed by retry, duplicate acknowledgments,
polling, missing resources, and administrator permissions. Migration tests include V5.

Stop the local broker without deleting its data with `docker compose stop`.

References: [Apache Kafka Docker](https://kafka.apache.org/41/getting-started/docker/)
and [Spring Kafka offset commits](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html).

The worker’s `KafkaRetryTests` uses an embedded Kafka broker. It checks that
a transient failure succeeds on retry, a persistent failure reaches the dead-letter
topic after three attempts, and the next message in the same partition is processed.
The API’s `OptimizationFlowTests` covers status, solution retrieval, and how a
dead-letter record changes a queued job to FAILED.
