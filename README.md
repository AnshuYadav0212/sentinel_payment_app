# sentinel_payment_app
This repository contains a microservices architecture implemented to detect and prevent fraud in digital payment workflows.

The platform is split into independently deployable **microservices** and uses REST for synchronous communication and Apache Kafka for asynchronous event-driven workflows. PostgreSQL provides transactional persistence, Redis is used for short-lived OTP state, and Docker is used to run infrastructure locally.
The platform is split into independently deployable microservices and uses REST for synchronous communication and Apache Kafka for asynchronous event-driven workflows. PostgreSQL provides transactional persistence, Redis is used for short-lived OTP state, and Docker is used to run infrastructure locally.

## Table of Contents
- Architecture
- Services
- Technology Stack
- High-Level Design
- Low-Level Design
- User Flow
- Transfer Flow
- Saga Compensation
- Fraud and OTP Flow
- Razorpay Payment Flow
- Entity Model
- Kafka Event Flow
- Database Design
- Caching
- Concurrency Handling
- Idempotency
- Observability
- Testing
- Performance Testing
- CI/CD
- Project Structure
- Environment Variables
- Running the Project
- API Flow
- Failure Scenarios
- Design Decisions
- Limitations and Future Improvements
-----
## Architecture

The system consists of six Spring Boot services:

```aiignore
                         ┌─────────────────────┐
                         │      Client         │
                         │ Postman / Frontend  │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │    API Gateway      │
                         │       :8080         │
                         └──────────┬──────────┘
                                    │
              ┌─────────────────────┼──────────────────────┐
              │                     │                      │
              ▼                     ▼                      ▼
      ┌───────────────┐     ┌───────────────┐      ┌───────────────┐
      │    Account    │     │  Transaction  │      │    Payment    │
      │    Service    │◄───►│    Service    │      │    Service    │
      │    :8081      │     │    :8082      │      │    :8083      │
      └───────┬───────┘     └───────┬───────┘      └───────┬───────┘
              │                     │                       │
              ▼                     ▼                       ▼
        ┌───────────┐         ┌───────────┐          ┌─────────────┐
        │ PostgreSQL│         │ PostgreSQL│          │  Razorpay   │
        └───────────┘         └─────┬─────┘          │  Test Mode  │
                                    │                └─────────────┘
                                    │
                              ┌─────▼─────┐
                              │   Kafka   │
                              │   :9092   │
                              └─────┬─────┘
                                    │
                      ┌─────────────┴──────────────┐
                      │                            │
                      ▼                            ▼
             ┌─────────────────┐          ┌─────────────────┐
             │ Fraud Detection │          │   Notification  │
             │     Service     │          │     Service     │
             └────────┬────────┘          └─────────────────┘
                      │
                      ▼
                    Redis
                 OTP / TTL State


       Observability
       ───────────────────────────────────────

       Spring Boot Actuator
                │
             Micrometer
                │
                ▼
           Prometheus
             :9090
                │
                ▼
             Grafana
             :3000


       Performance
       ───────────────────────────────────────

              k6
               │
               ▼
        Transaction APIs


```
----

## Services
| Service                 | Responsibility                                             |         Port |
| ----------------------- | ---------------------------------------------------------- | -----------: |
| API Gateway             | Single entry point and request routing                     |       `8080` |
| Account Service         | Account creation, lookup, debit, credit, status management |       `8081` |
| Transaction Service     | Transfer orchestration and transaction lifecycle           |       `8082` |
| Payment Service         | Razorpay order creation and payment verification           |       `8083` |
| Fraud Detection Service | Asynchronous fraud evaluation                              |       `8084` |
| Notification Service    | Transaction/refund/fraud notifications                     | configurable |

The Transaction Service communicates synchronously with the Account Service for account validation and balance operations and uses Kafka to publish transaction lifecycle events


----
## Technology Stack

### Backend
- Java 21
- Spring Boot
- Spring Web / REST
  - Spring Data JPA
- Hibernate
- Maven
- Lombok
- Bean Validation
### Distributed Systems
- Apache Kafka
- Event-driven architecture
- Saga compensation pattern
- Idempotent transaction handling
- Atomic database updates
- Redis
### Database
- PostgreSQL
- JPA/Hibernate
- HikariCP
- External Payment
- Razorpay Test Mode
- Server-side payment signature verification
### Infrastructure
- Docker
- Docker Compose
### Testing
- JUnit 5
- Mockito
- MockMvc
- Testcontainers
- Embedded Kafka
- WireMock
### CI / Code Quality
- GitHub Actions
- JaCoCo
### Observability
- Spring Boot Actuator
- Micrometer
- Prometheus
- Grafana
- Performance
- k6
---
## User Flow

1. Account Creation
```aiignore
User
 ↓
API Gateway
 ↓
Account Service
 ↓
Validate request
 ↓
Create Account
 ↓
PostgreSQL
 ↓
Return Account
```

2. Fund Transfer
   A normal transfer follows:
```aiignore
User
 │
 │ POST /transfer
 ▼
API Gateway
 │
 ▼
Transaction Service
 │
 ├── Validate amount
 │
 ├── Validate receiver account
 │
 ▼
Account Service
 │
 └── Atomic debit sender
 │
 ▼
Transaction DB
 │
 └── Save transaction = PROCESSING
 │
 ▼
Kafka
 │
 └── transaction.initiated
 │
 ▼
Fraud Detection
 │
 ├── Clean
 │      ↓
 │   transaction processing
 │
 └── Suspicious
        ↓
   PENDING_VERIFICATION
        ↓
      OTP
```

## Transition State Machine
```aiignore
                 ┌──────────────┐
                 │    PENDING   │
                 └──────┬───────┘
                        │
                        ▼
                 ┌──────────────┐
                 │  PROCESSING  │
                 └──────┬───────┘
                        │
              ┌─────────┴─────────┐
              │                   │
              ▼                   ▼
        Fraud Clean          Suspicious
              │                   │
              │                   ▼
              │          PENDING_VERIFICATION
              │                   │
              │             ┌─────┴──────┐
              │             │            │
              │          Valid OTP   Invalid/Expired
              │             │            │
              ▼             ▼            ▼
         COMPLETED       COMPLETED     FLAGGED
                                         │
                                         ▼
                                     REFUNDED
```

## Saga Compensation

The transfer operation spans multiple services and therefore cannot rely on one local database transaction.

The system uses **Choreography-Based** Saga-style compensation workflow.

### Forward Transaction
```aiignore
Debit Sender
     ↓
Save Transaction
     ↓
Publish Event
     ↓
Fraud Verification
     ↓
Complete Transaction
```

### Failure Path
```aiignore
Debit Sender
     ↓
Fraud / OTP Failure
     ↓
Mark transaction for compensation
     ↓
Credit Sender
     ↓
Publish transaction.refunded
     ↓
Notification
```

The compensation logic conditionally changes the transaction to a compensated/flagged state before crediting the sender and publishing the refund event.

## Fraud and OTP Flow
When the fraud service considers a transaction suspicious:
```aiignore
transaction.initiated
        ↓
Fraud Detection Service
        ↓
Suspicious
        ↓
PENDING_VERIFICATION
        ↓
Generate OTP
        ↓
Store OTP in Redis
        ↓
User submits OTP
```

### Correct OTP
```aiignore
OTP valid
   ↓
Delete OTP
   ↓
Complete transaction
   ↓
transaction.completed
```

### Wrong OTP
```aiignore
Wrong OTP
   ↓
Delete OTP
   ↓
Publish fraud.detected
   ↓
Compensate transaction
   ↓
Credit sender
   ↓
Publish transaction.refunded
```

### Expired OTP
```aiignore
OTP missing/expired
       ↓
Compensation
       ↓
Credit sender
       ↓
Refund event
```

## Razorpay Payment Flow
The external payment flow is separate from the internal transfer Saga.
```aiignore
User
 ↓
Payment API
 ↓
Validate internal account
 ↓
Create Razorpay Test Order
 ↓
Razorpay Checkout
 ↓
Payment completed
 ↓
Razorpay returns payment details
 ↓
Server-side signature verification
 ↓
Credit internal account
 ↓
Return payment confirmation
```
The system does **not** treat a browser/client success response as sufficient proof of payment. The backend verifies the Razorpay payment signature before updating the internal balance.

## Entity Model
### Account
```aiignore
Account
────────────────────────
id
accountNumber
accountHolderName
accountType
balance
email
phone
status
dailyTransactionLimit
createdAt
updatedAt
```

### Transaction
```aiignore
Transaction
────────────────────────
id
senderAccountNumber
receiverAccountNumber
amount
type
status
description
referenceNumber
failureReason
createdAt
completedAt
```

## Entity Relationship Diagram
The services are intentionally decoupled, so account and transaction data are owned by different services.

### Important design note

The logical relationship is represented through:
```aiignore
senderAccountNumber
receiverAccountNumber
```
rather than a cross-service JPA foreign key.

This keeps service ownership independent and avoids tightly coupling the Transaction Service's database schema to the Account Service database.

### Kafka Event Flow

Key events:
```aiignore
transaction.initiated
transaction.completed
transaction.refunded
fraud.detected
```

 #### Transaction initiated
```aiignore
Transaction Service
        │
        ▼
transaction.initiated
        │
        ▼
Fraud Detection Service
```

#### Transaction completed
```aiignore
Transaction Service
        │
        ▼
transaction.completed
        │
        ▼
Notification Service
```

#### Refund
```aiignore
Transaction Service
        │
        ▼
transaction.refunded
        │
        ▼
Notification Service
```

#### Fraud
```aiignore
Transaction Service
        │
        ▼
fraud.detected
        │
        ▼
Account / Fraud handling
```

## Redis Usage
```aiignore
Generate OTP
    ↓
Redis SET
    ↓
TTL
    ↓
User verifies
    ↓
OTP removed
```
 Redis is here used as ephemeral verification state rather than source of truth for account balance.

## Concurrency Handling

Two concurrent requests can both read the same old balance.

The Account Service instead uses an atomic conditional database update:
The service checks the affected row count:
```
1 row updated → debit succeeded
0 rows updated → insufficient balance / invalid account state
```

This moves the race-sensitive condition into the database.

The project includes a concurrency integration test against PostgreSQL where simultaneous debit requests are executed and the final balance is verified.

## Idempotency
The transaction verification flow therefore checks terminal states before processing another OTP verification.
```aiignore
COMPLETED
   ↓
Duplicate verification
   ↓
Return existing transaction
```
similarly,
```aiignore
COMPLETED
   ↓
Duplicate verification
   ↓
Return existing transaction
```

## Observability
The application exposes metrics through:
```aiignore
Spring Boot Actuator
        ↓
Micrometer
        ↓
Prometheus
        ↓
Grafana
```
### Business Metrics
The Transaction Service exposes application-specific metrics such as:
```aiignore
transactions_initiated_total
transactions_completed_total
transactions_failed_total
transactions_fraud_detected_total
```
### Runtime Metrics
The monitoring stack can also expose:
```aiignore
Service availability
HTTP request rate
HTTP latency
JVM memory
CPU usage
```

## Grafana Dashboard
This helps to visualize the metrics for the application
Dashboard contains the panels for:
```aiignore
Service Health
Transaction Initiated
Transactions Completed
Transactions Failed
Fraud Detected
Completion Rate
Request Throughput
Average HTTP Latency
JVM / CPU
```
-------
## Testing

| Test Type                 | Purpose                    |
| ------------------------- | -------------------------- |
| Unit Tests                | Business/service logic     |
| MockMvc                   | REST/controller behavior   |
| PostgreSQL/Testcontainers | Real database integration  |
| Embedded Kafka            | Event-driven workflows     |
| WireMock                  | Downstream HTTP failures   |
| Concurrency Tests         | Concurrent balance updates |

### Important scenarios
Test covers these failed path:
- account validation
- transaction validation
- insufficient balance
- successful transfers
- duplicate OTP verification
- expired OTP
- invalid OTP
- Saga compensation
- Kafka event handling
- downstream service failures
- concurrent debits

## Jacoco
Critical paths such as transaction processing, compensation and concurrency are covered with targeted tests.
Current core services coverage is approximately:

```aiignore
Account Service
Instruction Coverage: ~62%

Transaction Service
Instruction Coverage: ~59%
```
----
## Performance Testing
Utilized  k6 to generate concurrent HTTP traffic for transaction API
```
5 VUs  → baseline
20 VUs → medium load
50 VUs → higher concurrency
```
and done measurement for:
```aiignore
Throughput
Average latency
p95 latency
Error rate
```
----
## CI/CD
Implemented the **Continuous Integration pipeline** for now
```aiignore

Git Push / Pull Request
        ↓
GitHub Actions
        ↓
Checkout
        ↓
Setup Java 21
        ↓
Maven dependency cache
        ↓
mvn verify
        ↓
Automated tests
```
----
## Project Run
### Prerequisites
```aiignore
Java 21
Maven
Docker Desktop
Git
k6
```

### Start Docker Infrastructure
Start Docker Infrastructure

From the project root:
```aiignore
docker compose up -d
```
Check running containers:
```aiignore
docker ps
```

### Start the Spring Boot Services

From each service directory:

```aiignore
mvn spring-boot:run
```

