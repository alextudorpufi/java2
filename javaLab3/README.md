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
attempts six borrows, returns a book, and borrows it again. It also tries an
unavailable copy, a textbook, and a reference book, and prints each loan's due date. The demo is opt-in so ordinary startup leaves the repositories empty.
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
  policy: fixed             # fixed, role-based, or drools
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
- `BorrowingService` receives repositories, settings, and the event publisher
  through its constructor. `borrowBook(new BorrowingRequest(memberId, bookId))`
  creates a loan with a due date; `returnBook(memberId, bookId)` removes it.
  The selected policy is constructor-injected into the validation aspect.
  `BorrowingPolicy.evaluate(context)` returns permission, a rejection reason, and
  a borrowing period. Fixed and role-based policies use a 14-day period.
- `BorrowingValidationAspect` wraps both operations. Before borrowing it checks
  the member and book exist, then evaluates the selected policy exactly once.
  The policy checks availability and eligibility. An approved decision is attached
  to the per-call `BorrowingRequest`, so the service uses the same period without
  evaluating the rules again. Before returning it checks that
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

## Advanced: Drools borrowing rules

Run the bonus:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --library.demo-enabled=true --library.policy=drools"
```

For the presentation, focus on just two files:

- **`src/main/resources/rules/borrowing.drl`** contains five rules: reject an
  unavailable book, reject a reference book, and allow a student, regular member,
  or premium member under their limit. Each `when` checks the facts; its `then`
  adds a `BorrowingDecision` to a result list.
- **`DroolsBorrowingPolicy.java`** loads the file once. For each request it creates
  a stateless session, supplies the result list as a global, and calls
  `session.execute(context)`. Drools inserts the context and runs matching rules.
  Stateless execution handles session cleanup automatically.

`BorrowingContext` is the input: member, book, current loan count, and availability.
`BorrowingDecision` is the output: allowed/rejected, reason, and borrowing days.
The result list is new for each request. The five rules have mutually exclusive
conditions, so at most one produces a decision; when none matches, borrowing is
rejected. Keep conditions mutually exclusive when editing or adding rules.

| Membership | Maximum active loans | Normal book period | Textbook period |
| --- | --- | --- | --- |
| Student | 2 | 14 days | 7 days |
| Regular | 3 | 21 days | 7 days |
| Premium | 5 | 28 days | 7 days |

Unavailable books and reference books are rejected. Drools mode reads limits and
periods from the DRL file; YAML limits apply to the original Java policies.

The validation aspect calls this policy once and passes its approved decision to
the service through `BorrowingRequest`. The service calculates the due date from
the returned period. Logging and events still run as in the homework.

To demonstrate a rule change, change `? 7 : 28` to `? 7 : 35` in the premium rule,
then restart with the Maven command above. Premium normal-book loans now last
35 days without editing `BorrowingService`. Rebuild the jar if running the packaged
application. Existing loans keep their due dates.

A short explanation for the professor:

> I pass a borrowing context as a fact to Drools. The engine checks the `when`
> conditions in my rule file and executes the matching `then` block. That block
> produces a decision and borrowing period. My service uses the result, so changing
> the rules does not require changing the service.

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
