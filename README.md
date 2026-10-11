# FinAtom

Core banking engine simulating payment infrastructure behind modern neobanks (Revolut / Wise style). Built as a portfolio project to demonstrate production-grade microservices patterns in Java.

---

## Prerequisites

- Java 21
- Docker Desktop with Kubernetes enabled
- `make`

---

## Architecture

```mermaid
graph TD
    User(["👤 Client"])

    subgraph k8s["☸ Kubernetes Cluster"]
      subgraph svc["Microservices"]
        AC["account-service\nSparkJava · gRPC · Flyway · jOOQ"]
        PS["payment-service\nSparkJava · gRPC · Kafka · Redis · jOOQ"]
        AF["antifraud-service\nSparkJava · Kafka · jOOQ"]
        NS["notification-service\nKafka"]
      end
      subgraph infra["Infrastructure"]
        PG[("PostgreSQL")]
        RD[("Redis")]
        KF["Kafka"]
      end
      subgraph obs["Observability"]
        PR["Prometheus"]
        GF["Grafana"]
        LK["Loki + Promtail"]
      end
    end

    FX["Frankfurter API\nexchange rates"]

    User -->|"REST"| AC
    User -->|"REST"| PS
    PS -->|"gRPC KYC"| AC
    AC --> PG
    PS --> PG
    AF --> PG
    PS --> RD
    PS -->|"publish"| KF
    KF -->|"consume"| AF
    KF -->|"consume"| NS
    PS -.->|"HTTP"| FX
    AC & PS & AF -->|"scrape"| PR
    PR --> GF
    AC & PS & AF -->|"logs"| LK
    LK --> GF
```

---

## Payment Flow

```mermaid
sequenceDiagram
    actor C as Client
    participant PS as payment-service
    participant AS as account-service
    participant RD as Redis
    participant FX as Frankfurter API
    participant DB as PostgreSQL
    participant KF as Kafka
    participant AF as antifraud-service

    C->>PS: POST /payments
    PS->>RD: exists(idempotencyKey)?
    RD-->>PS: false

    PS->>AS: gRPC validateKyc(fromWallet)
    AS-->>PS: OK
    PS->>AS: gRPC validateKyc(toWallet)
    AS-->>PS: OK

    PS->>RD: get rate:USD:EUR
    alt cache miss
        PS->>FX: GET /latest?from=USD&to=EUR
        FX-->>PS: { rate: 0.92 }
        PS->>RD: setex rate:USD:EUR 3600
    end

    PS->>DB: BEGIN TRANSACTION
    PS->>DB: INSERT INTO payments
    PS->>DB: INSERT INTO ledger (DEBIT)
    PS->>DB: INSERT INTO ledger (CREDIT)
    PS->>DB: UPDATE wallets SET balance - amount
    PS->>DB: UPDATE wallets SET balance + converted
    PS->>DB: UPDATE payments SET status=COMPLETED
    PS->>DB: COMMIT

    PS->>RD: setex idempotencyKey 86400
    PS->>KF: publish payment.completed

    KF-->>AF: consume payment.completed
    AF->>DB: analyze fraud
    opt amount > 10000 or high frequency
        AF->>DB: INSERT INTO fraud_alerts
    end

    PS-->>C: 201 Created
```

---

## Database Schema

```mermaid
erDiagram
    accounts {
        uuid id PK
        varchar name
        varchar email UK
        kyc_status kyc_status
        timestamp created_at
    }
    wallets {
        uuid id PK
        uuid account_id FK
        varchar currency
        numeric balance
        timestamp updated_at
    }
    payments {
        uuid id PK
        uuid from_wallet_id FK
        uuid to_wallet_id FK
        numeric amount
        varchar currency
        payment_status status
        varchar idempotency_key UK
        timestamp created_at
    }
    ledger {
        uuid id PK
        uuid wallet_id FK
        numeric amount
        entry_type type
        uuid payment_id FK
        timestamp created_at
    }
    fraud_alerts {
        uuid id PK
        uuid payment_id FK
        uuid wallet_id FK
        alert_reason reason
        timestamp created_at
    }

    accounts ||--o{ wallets : "has"
    wallets ||--o{ payments : "from_wallet"
    wallets ||--o{ payments : "to_wallet"
    payments ||--|{ ledger : "entries"
    payments ||--o{ fraud_alerts : "triggers"
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| HTTP | SparkJava |
| Database | PostgreSQL 16 |
| SQL | jOOQ (no ORM — typed SQL) |
| Migrations | Flyway |
| Connection Pool | HikariCP |
| Cache | Redis (idempotency keys, exchange rate cache) |
| Messaging | Apache Kafka |
| Service-to-service | gRPC (KYC validation) |
| Config | Typesafe Config |
| Metrics | Micrometer + Prometheus + Grafana |
| Logs | SLF4J + Loki + Promtail + Grafana |
| Tests | Spock Framework (BDD) + Testcontainers |
| Load testing | k6 |
| Infrastructure | Docker Compose + Kubernetes (Docker Desktop) |

---

## Key Engineering Decisions

**Idempotency** — duplicate payment requests never create a second transfer; Redis-backed keys with 24h TTL

**Double-spend protection** — pessimistic `balance >= amount` check inside a PostgreSQL transaction prevents concurrent overdrafts

**Audit trail** — every balance change recorded in an append-only ledger (DEBIT / CREDIT entries)

**Fraud detection** — high-amount (> $10k) and high-frequency (> 10 payments/60min) rules via Kafka consumer

**Exchange rate caching** — live rates from Frankfurter API cached in Redis (1h TTL) to avoid repeated external calls

**Clean architecture** — domain exceptions mapped to HTTP codes, typed record DTOs, wiring extracted to `AppConfig` classes

---

## Observability

77 000 payment requests processed under k6 load test (ramp 1→70 VUs, 12 min):

![Grafana Dashboard](docs/grafana-dashboard.webp)

- **76 985** completed payments, **14** failed (99.98% success rate)
- Peak throughput **~180 req/s**, latency p99 < 800ms at spike
- JVM heap stable at 8–16 MiB — no memory leaks

Structured access logs shipped via Promtail → Loki, queryable in Grafana Explore:

![Grafana Loki Logs](docs/grafana-logs.webp)

---

## Services

| Service | Port | Responsibilities |
|---|---|---|
| account-service | 8081 | Accounts, multi-currency wallets, KYC, deposits |
| payment-service | 8082 | Transfers, currency conversion, idempotency, ledger |
| antifraud-service | 8083 | Fraud scoring, velocity rules |
| notification-service | — | Async event logging (Kafka consumer) |

---

## API

### account-service

```
POST   /accounts                  Create account (auto-creates USD/EUR/GBP wallets)
GET    /accounts/:id              Get account
GET    /accounts/:id/wallets      List wallets
PUT    /accounts/:id/kyc/verify   Verify KYC
POST   /wallets/:id/deposit       Deposit funds
```

### payment-service

```
POST   /payments                  Create payment (idempotent)
```

---

## Running

```bash
make docker-build   # build all Docker images
make deploy         # deploy to Kubernetes
make port-forward   # expose services on localhost
make load-test      # run k6 load test via Docker
make status         # show pod status
```

### Endpoints after `make port-forward`

| Service | URL |
|---|---|
| account-service API | http://localhost:8081 |
| payment-service API | http://localhost:8082 |
| Grafana dashboards | http://localhost:3000 (admin / finatom) |

---

## Testing

```bash
# Unit + integration tests (Spock BDD + Testcontainers)
./gradlew test
```

Integration tests spin up real containers and cover: balance transfers between accounts, idempotency, insufficient funds, ledger entries.
