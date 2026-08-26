# FinFlow 💸

FinFlow is a high-performance, microservices-based fintech platform (similar to Stripe or Revolut). This project serves as a production-ready demonstration of distributed systems design, strict transactional consistency, fault tolerance, and enterprise-grade observability.

## 🚀 Core Features (Business Logic)
- Multi-Currency Wallets: User account creation, balance management, and real-time currency exchange.
- Transactional Engine: Peer-to-peer (P2P) transfers with strict ACID guarantees and double-spending protection via Idempotency keys.
- Antifraud Scoring: Automated, real-time transaction screening based on velocity rules and account limits.
- Asynchronous Notifications: Event-driven system alerts triggered by account activities and ledger updates.

## 🛠 Tech Stack
- Backend: Java 25, Spring Boot 3.x, Spring Cloud
- Data & Storage: PostgreSQL, Redis (Caching & Distributed Locks), Flyway (Database Migrations)
- Messaging: Apache Kafka (Event-driven asynchronous communication)
- Testing: JUnit 5, Mockito, Testcontainers (Spinning up real DB & Kafka instances in Docker for integration tests)
- DevOps & Observability: Docker, Docker Compose, Prometheus, Grafana, OpenTelemetry

## 📐 System Architecture
*(Architectural diagram from Miro/Draw.io will be placed here)*

The system is split into isolated domain contexts (DDD approach):
- Account Service — Manages users, multi-currency balances, and core ledger accounts.
- Transaction Service — Handles payments, peer-to-peer transfers, and core banking operations.
- Antifraud Service — Evaluates transaction risks before they are executed.
- Notification Service — Consumes ledger events to send real-time user updates.

---
*This project is under active development.*
