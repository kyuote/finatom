# FinAtom 💸

FinAtom is a core banking engine that simulates the payment infrastructure behind modern neobanks like Revolut and Wise.

---

## What It Does

- User registers → gets multi-currency wallets (USD, EUR, GBP)
- Top up balance via mocked external bank
- Send money to another user (P2P transfer)
- Auto currency conversion if wallets differ
- Every payment is checked by antifraud before execution
- Receiver gets an async notification via Kafka event

---

## Architecture

Four independent microservices communicating over Kafka:

```
account-service      — users, wallets, balances
payment-service      — transfers, currency conversion, ledger
antifraud-service    — fraud scoring, velocity rules, blocking
notification-service — async event-driven alerts
```

Event flow:

```
payment-service  →  payment.created   →  antifraud-service
antifraud-service →  payment.approved  →  payment-service
payment-service  →  payment.completed →  notification-service
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 + Virtual Threads |
| Web | SparkJava |
| Database | PostgreSQL 16 |
| SQL | jOOQ (no ORM — typed SQL) |
| Migrations | Flyway |
| Connection Pool | HikariCP |
| Cache / State | Redis (idempotency keys, rate limiting) |
| Messaging | Apache Kafka |
| Testing | Spock Framework (BDD — given / when / then) |
| Config | Typesafe Config |
| Infrastructure | Docker Compose + Kubernetes manifests |

---

## Key Technical Problems Solved

**Idempotency** — duplicate payment request never creates a second transfer (Redis-backed idempotency keys)

**Double-spend protection** — pessimistic locking in PostgreSQL prevents concurrent overdrafts

**Audit trail** — every balance change is recorded in an append-only ledger

**Velocity rules** — more than 5 payments per minute triggers automatic fraud block

**Currency conversion** — transfers between different currency wallets auto-convert at mocked exchange rates

---

## Quick Start

```bash
# Start infrastructure (PostgreSQL, Redis, Kafka)
docker compose up -d

# Run account-service
./gradlew :account-service:run
```

API available at `http://localhost:8081`

---

## Project Status

Under active development.
