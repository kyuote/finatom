# FinAtom 💸

FinAtom is a high-performance fintech platform built on microservices, in the spirit of Stripe / Revolut. The project demonstrates production-ready distributed systems architecture: strict transactional consistency, fault tolerance, and enterprise-grade observability.

## 🚀 Core Business Logic

- **Multi-Currency Wallets** — account creation, balance management, real-time currency exchange.
- **Transaction Engine** — P2P transfers with ACID guarantees and double-spend protection via Idempotency Keys.
- **Antifraud Scoring** — automated transaction checks against velocity rules and limits.
- **Async Notifications** — event-driven alerts triggered by account activity.

---

## 🛠 Tech Stack

| Layer | Technology | Why |
|---|---|---|
| **Language** | Java 21 + Virtual Threads | Handles millions of transactions without reactive complexity — written like sync code, scales like async |
| **Build** | Gradle (Monorepo) | Single repository for all microservices, flexible without XML |
| **Database** | PostgreSQL | Reliable ACID storage — money needs transactions |
| **Connection Pool** | HikariCP | The fastest JVM connection pool — minimal overhead |
| **Data Access** | jOOQ | No Hibernate. Strictly typed SQL directly in Java — predictable and fast |
| **DB Migrations** | Flyway | Version-controlled schema — every change is tracked |
| **Web Server** | Javalin 7 | Ultra-lightweight microframework instead of heavy Spring Boot |
| **Cache** | Redis + Jedis | In-memory cache and ephemeral state — idempotency keys, rate limiting |
| **Testing** | Spock Framework | BDD tests in Groovy (given / when / then) — read like documentation |
| **Config** | Typesafe Config | Structured `application.conf` instead of hardcoded values |
| **Infrastructure** | Docker Compose | Spin up PostgreSQL + Redis with a single command |

---

## 📐 Architecture

The system is split into isolated domain contexts (DDD):

- **Account Service** — user management, multi-currency balances, and the core ledger.
- **Transaction Service** — payments, P2P transfers, and core banking operations.
- **Antifraud Service** — risk scoring before a transaction is executed.
- **Notification Service** — consumes ledger events and pushes alerts to users.

*(Architecture diagram coming soon)*

---

## ⚡ Quick Start

### 1. Start infrastructure

```bash
docker compose up -d
```

Starts **PostgreSQL** on `5432` and **Redis** on `6379`.

### 2. Build and run Account Service

```bash
./gradlew :account-service:run
```

Or in IntelliJ IDEA: open the **Gradle** tab → click **Reload** (⟳) → run `account-service → Tasks → application → run`.

Service starts on **http://localhost:8081**

### 3. Health check

```bash
curl http://localhost:8081/health
# {"status":"UP"}
```

---

## 🔍 Why does the project show grey/red in IntelliJ IDEA?

We don't use Spring Boot — the project is built on plain Gradle. IntelliJ doesn't always auto-detect custom build scripts and may treat source files as plain text without project context.

**Fix in 2 steps:**
1. Open the **Gradle** tab on the right side of the IDE.
2. Click the circular arrows **Reload All Gradle Projects** (⟳).

IntelliJ will re-import the project, pull all dependencies from `build.gradle`, and enable syntax highlighting, autocompletion, and navigation.

---

*Project is under active development.*
