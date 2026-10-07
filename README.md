# One-Time Coupon System

A backend service for validating and redeeming one-time 100% discount coupons.

The system guarantees that a coupon can be successfully redeemed only once per user, including when multiple redemption requests are made concurrently.

## Tech Stack

- Java 17
- Spring Boot 4.0.8
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Maven
- Docker / Docker Compose
- Testcontainers
- JUnit 5
- Swagger / OpenAPI

## Features

- Validate coupons without modifying state
- Apply a 100% discount coupon
- One successful redemption per user and coupon
- User-specific coupon eligibility
- Active and validity-period checks
- Transaction rollback on processing failure
- Database-level concurrency protection
- Unique database constraint as additional duplicate protection
- Automated integration and concurrency tests
- Swagger/OpenAPI documentation
- Postman collection
- Dockerized application and PostgreSQL database

## Architecture

The project is implemented as a Spring Boot modular monolith.

Main data model:

- `app_users` - users of the system
- `coupons` - coupon definitions
- `coupon_eligibilities` - identifies which users can use which coupons
- `coupon_transactions` - successful coupon transactions
- `coupon_redemptions` - records successful one-time coupon usage

Coupon usage is tracked per `(user, coupon)` pair rather than by storing a global `used` flag on the coupon.

This allows the same coupon code to be used by multiple eligible users while ensuring each individual user can redeem it only once.

## API Endpoints

### Validate Coupon

`POST /validate-coupon`

Checks whether:

- the user exists
- the coupon exists
- the coupon is active
- the coupon is within its validity period
- the coupon is valid for the user
- the user has not already redeemed it

Validation does **not** modify application state.

Example request:

```json
{
  "user_id": "user-123",
  "coupon_code": "WELCOME100",
  "amount": 1000
}
```

Example response:

```json
{
  "valid": true,
  "original_amount": 1000,
  "discount": 1000,
  "final_amount": 0
}
```

### Apply Coupon

`POST /apply-coupon`

The endpoint revalidates the coupon inside a database transaction before applying it.

For a 100% coupon:

```text
original amount = 1000
discount        = 1000
final amount    = 0
```

A successful request creates a transaction and records the coupon redemption.

Example request:

```json
{
  "user_id": "user-123",
  "coupon_code": "WELCOME100",
  "amount": 1000
}
```

## Concurrency Handling

The application protects coupon redemption using database-level pessimistic locking.

During `/apply-coupon`, the application obtains a `PESSIMISTIC_WRITE` lock on the coupon eligibility row for the specific `(user, coupon)` pair.

Conceptually:

```text
Request A ──┐
Request B ──┼── same user + coupon
Request C ──┘
             |
             v
      SELECT ... FOR UPDATE
             |
             v
       one request proceeds
             |
             v
      transaction + redemption
             |
           COMMIT
             |
             v
other requests acquire the lock
and detect existing redemption
```

Therefore, concurrent requests for the same user and coupon are serialized.

Only one request can successfully create the redemption.

A database unique constraint on `(user_id, coupon_id)` in `coupon_redemptions` provides an additional defense-in-depth guarantee against duplicate redemption.

Different users use different eligibility rows, so they are not unnecessarily serialized when redeeming the same coupon.

## Why `/apply-coupon` Validates Again

`/validate-coupon` is intentionally read-only.

A successful validation response does not guarantee that the coupon will still be available later because another request could redeem it between validation and application.

For this reason, `/apply-coupon` performs validation again inside its transaction while holding the appropriate database lock.

## Transaction Failure Handling

Coupon redemption is recorded only after successful transaction processing.

Flow:

```text
validate
   |
lock eligibility
   |
check existing redemption
   |
calculate discount
   |
process transaction
   |
   +-- failure --> rollback / coupon remains unused
   |
 success
   |
save transaction
   |
save redemption
   |
 commit
```

If transaction processing throws an exception, Spring's transaction is rolled back.

No successful transaction or redemption is persisted, so the user can retry the coupon.

## Running with Docker

### Prerequisites

- Docker Desktop / Docker Engine
- Docker Compose

No local Java, Maven, PostgreSQL, or IDE configuration is required when running the application with Docker Compose.

Clone the repository:

```bash
git clone https://github.com/hariharanus96-cyber/one-time-coupon-system.git
cd one-time-coupon-system
```

Start the complete application:

```bash
docker compose up --build
```

Docker Compose starts:

- PostgreSQL 17
- Spring Boot application

Once startup is complete, the application is available at:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI specification:

```text
http://localhost:8080/v3/api-docs
```

Stop the application with:

```bash
docker compose down
```

To also remove the PostgreSQL development volume:

```bash
docker compose down -v
```

## Running Locally

### Prerequisites

- Java 17
- PostgreSQL 17, or Docker for running PostgreSQL
- Maven installation is not required because the Maven Wrapper (`mvnw` / `mvnw.cmd`) is included

Verify Java:

```bash
java -version
```

The application requires Java 17. Any compatible Java 17 JDK distribution can be used for local development.

If PostgreSQL is being run through Docker, start it with:

```bash
docker compose up -d postgres
```

Then run the Spring Boot application.

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

### Linux / macOS

```bash
./mvnw spring-boot:run
```

Default database configuration:

```text
Database: nativewit_coupon_db
Username: nativewit
Password: nativewit123
Port:     5432
```

Database settings can also be overridden using:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

## Demo Data

The application seeds development data including:

```text
Users:
user-123
user-456

Coupons:
WELCOME100  - active 100% discount coupon
INACTIVE100 - inactive coupon
```

Both demo users are eligible for `WELCOME100`.

`user-123` is also associated with `INACTIVE100` for testing inactive coupon behavior.

Because PostgreSQL data is persisted in a Docker volume, a previously redeemed demo coupon remains redeemed between restarts.

For a completely fresh demo database:

```bash
docker compose down -v
docker compose up --build
```

## Automated Tests

Tests use Testcontainers with a real PostgreSQL database rather than an in-memory database.

Docker must be running because Testcontainers starts a PostgreSQL container during the integration tests.

### Windows

```powershell
.\mvnw.cmd clean test
```

### Linux / macOS

```bash
./mvnw clean test
```

The automated test suite covers:

1. Valid coupon validation
2. Invalid/nonexistent coupon
3. Inactive coupon
4. Correct 100% discount calculation
5. Successful coupon application
6. Already-used coupon rejection
7. Multiple users using the same coupon
8. Transaction failure does not consume the coupon
9. Concurrent redemption behavior

The concurrency test launches multiple simultaneous redemption attempts for the same user and coupon and verifies that exactly one succeeds and exactly one transaction/redemption is stored.

## API Documentation

Interactive API documentation is available through Swagger UI after starting the application:

```text
http://localhost:8080/swagger-ui.html
```

The OpenAPI specification is available at:

```text
http://localhost:8080/v3/api-docs
```

A Postman collection is also included under:

```text
postman/One-Time-Coupon-System.postman_collection.json
```

## Design Decisions and Trade-offs

### Pessimistic Locking

Pessimistic row locking was selected because the primary requirement is correctness under concurrent redemption attempts.

It makes the one-time redemption flow explicit and straightforward to reason about.

### Unique Database Constraint

The `(user_id, coupon_id)` unique constraint provides defense in depth even if application-level concurrency logic changes in the future.

### Eligibility Row as Lock Target

Locking the specific user/coupon eligibility row means requests for the same user and coupon are serialized while different users can still redeem the same coupon concurrently.

### PostgreSQL for Integration Tests

Concurrency behavior depends on real database locking semantics, so Testcontainers is used to test against PostgreSQL instead of relying on an in-memory database.

### Transaction Processing Abstraction

Transaction processing is represented by a `TransactionProcessor` abstraction.

This makes transaction failure behavior testable without coupling coupon logic to a specific external payment provider.

In a production system involving external payment side effects, additional patterns such as idempotency keys, an outbox, or a saga may be appropriate.

### Schema Management

Hibernate `ddl-auto` is used for this assessment to keep local setup simple.

For a production application, database migrations such as Flyway or Liquibase would be preferred.

## Submission

This repository contains the complete implementation, automated tests, Docker configuration, Swagger/OpenAPI documentation, and Postman collection for the One-Time Coupon System technical assessment.