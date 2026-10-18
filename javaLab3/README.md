# Enterprise Architectures — Lab 3

Spring Boot 4.1.1, Java 21+, Maven wrapper. Lab 3 lives in `com.example.library`.
The earlier exercises remain in `com.example.demo` and `com.example.bonus`.
The Maven plugin now starts `LibraryApplication` by default; its component scan is
limited to the library package, so it does not require the earlier CSV/JSON profiles.

## Run the lab

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --library.demo-enabled=true"
```

This prints the injection observations, creates premium member Ana and some books,
borrows up to her limit, demonstrates a rejected borrow, returns a book, and borrows
another. The demo is opt-in so ordinary startup leaves the repositories empty.
No HTTP endpoints are needed for this lab: `LibraryDemo` calls the Spring service.

Try the role policy:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --library.demo-enabled=true --library.policy=role-based"
```

The default fixed policy allows Ana 3 books; the role policy allows her 5.

## Configuration

These settings are in `src/main/resources/application.yaml`; edit them there or pass
the same keys as command-line arguments. All values below are the defaults:

```yaml
library:
  policy: fixed             # fixed or role-based
  fixed-limit: 3
  student-limit: 2
  regular-limit: 3
  premium-limit: 5
  event-threshold: 2
  demo-enabled: false
```

`LibraryProperties` binds and validates configuration. Unknown policies, nonpositive
limits, and negative thresholds prevent startup. `BorrowingConfiguration` creates
exactly one `BorrowingPolicy` bean. To add a policy, implement the interface and
extend the configuration selection. Services never instantiate their dependencies.

## Compulsory: injection order

`injection/InjectionOrderDemo` uses constructor, field, setter, and arbitrary method
injection on the same bean. The constructor observes a null field; both injected
methods assert that the constructor and field dependencies already exist.
`@PostConstruct` verifies completion and prints the observations.

The demonstrated order is **constructor → field → setter and method**. Setter and
arbitrary method injection belong to the same phase; the example and tests make no
assumption about their relative order. The field entry records an observation of
its injected value, rather than pretending a field assignment invokes a callback.
See [Spring's Autowired contract](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/annotation/Autowired.html).

## Homework: borrowing flow

- `Member`, `Book`, and `Loan` are immutable domain records. One book ID represents
  one physical copy. The three repositories store members, books, and active loans
  in memory. Availability and member counts are derived from active loans.
- `BorrowingService` receives repositories, policy, settings, and event publisher
  through its constructor. `borrowBook(memberId, bookId)` creates a loan;
  `returnBook(memberId, bookId)` removes it. `borrowingLimit(memberId)` exposes the
  selected policy's limit.
- `BorrowingValidationAspect` wraps both operations. Before borrowing it checks
  the member, book, availability, and policy limit. Before returning it checks that
  the member owns the active loan. Violations throw `BorrowingException` before
  modifying loans. A lock on the loan repository covers validation and mutation,
  preventing concurrent requests from passing stale checks.
- `BorrowingLoggingAspect` wraps validation and execution (order 10, with validation
  at order 20). Its `@Around` advice logs operation name, member name, book title,
  and elapsed milliseconds, including failures. Its `@AfterThrowing` advice logs
  the failed operation and exception message, and preserves the exception.
- After **each successful borrow** with active loans strictly greater than
  `event-threshold`, the service publishes `BorrowingThresholdExceeded`.
  `BorrowingEventListener` handles it synchronously and logs the member, book,
  count, and threshold. Returning and rejected borrowing publish no such event.

Spring AOP works through proxies: obtain `BorrowingService` from Spring and call
its public operations. Constructing it with `new`, or calling an advised method
from another method on the same instance, bypasses the aspects. The demo calls the
injected proxy. [Spring Boot AOP documentation](https://docs.spring.io/spring-boot/4.0/reference/features/aop.html)
explains automatic proxy configuration with AspectJ on the classpath.

This is a single-process teaching example. Data disappears on restart; events are
synchronous and are not durable notifications. Production persistence would need
transactional constraints and an appropriate event delivery strategy.

## Verify

```sh
# Lab 3: integration, policy configuration, DI, logging, events, and concurrency
./mvnw -Dtest='com.example.library.*Tests' test

# Entire repository, including earlier exercises that require a data-reader profile
./mvnw -Dspring.profiles.active=dev test

# Build the executable jar (main class: LibraryApplication)
./mvnw -Dspring.profiles.active=dev package
java -jar target/demo-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --library.demo-enabled=true
```

To launch the earlier application explicitly:

```sh
./mvnw spring-boot:run -Dstart-class=com.example.demo.DemoApplication -Dspring-boot.run.profiles=dev
```
