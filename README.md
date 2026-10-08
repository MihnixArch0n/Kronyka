# Kronyka: Dual-Engine Auction Platform

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-green.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Exposed](https://img.shields.io/badge/Exposed-1.5.0-purple.svg)](https://github.com/JetBrains/Exposed)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg?logo=postgresql)](https://www.postgresql.org)
[![Docker](https://img.shields.io/badge/Container-Docker%20%7C%20Podman-blue.svg?logo=docker)](https://www.docker.com)

**Kronyka** is a backend REST service implementing a **Dual-Engine Auction Platform** built with **Kotlin**, **Spring Boot**, and **JetBrains Exposed SQL DSL**, adhering to strict **Clean Architecture** principles.

---

## 1. Problem Statement & Core Engines

Conventional open English auctions suffer from two major flaws: **last-second sniping** and **shill bidding / insider collusion**. Kronyka resolves both using two distinct game-theoretic auction mechanisms:

1. **Engine 1 — Vickrey Sealed-Bid Blind Auction (Nobel Prize in Economics 1996)**:
   - A 3-phase state machine (`COMMIT` -> `REVEAL` -> `SETTLED`).
   - Bidders submit a cryptographic SHA-256 blind hash with a blind deposit to prevent front-running and operator collusion.
   - Winner is the highest bidder, but pays the **second-highest bid price** (Second-Price Rule).
2. **Engine 2 — Step-Decay Dutch Auction**:
   - Descending-price auction evaluated via a stateless staircase formula in memory.
   - Resolves concurrency race conditions on "Buy Now" requests using atomic optimistic locking (`version`), returning HTTP `409 Conflict` to latecomers.

---

## 2. Project Architecture & Layering

Kronyka strictly follows **Clean Architecture (Hexagonal / Ports & Adapters)**:
- **Presentation Layer (`presentation/`)**: REST controllers, DTO validation (`@Valid`), and global exception mapping.
- **Domain Layer (`domain/`)**: Pure business logic (entities, use cases, math formulas, repository ports). **Zero dependencies** on Spring Web, Exposed, or JDBC libraries.
- **Infrastructure Layer (`infrastructure/`)**: Implements repository ports using JetBrains Exposed SQL DSL, manages PostgreSQL connections, and handles centralized JWT authentication.

> 📖 **Detailed Architecture Document**: See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for package layouts, layer boundaries, and Mermaid sequence diagrams.

---

## 3. Quick Reference & Documentation Index

All in-depth specifications are maintained under the [`docs/`](docs/) directory:

- 🏛️ **Architecture & Design**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Clean Architecture layers, sequence flows, and concurrency control.
- 📡 **REST API Specification**: [docs/API_SPEC.md](docs/API_SPEC.md) — Detailed contract for all 11 endpoints, request/response models, and status codes.
- 🗄️ **Database Schema & DDL**: [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) — Tables (`users`, `auctions`, `vickrey_bids`), data types, constraints, and indexes.
- 🤖 **AI Agents Context**: [docs/llm/](docs/llm/) — Context documentation and technical invariants for AI coding assistants (referenced in [AGENTS.md](AGENTS.md)).

---

## 4. Course Requirements Compliance (Phase 1)

This project strictly adheres to all requirements outlined in the course syllabus:

| Requirement | Implementation in Kronyka |
| :--- | :--- |
| **REST API (JSON)** | Standard RESTful API communicating via JSON over HTTP. |
| **HTTP Methods** | Covers `POST`, `GET`, and `DELETE` across 11 endpoints. |
| **API Documentation** | OpenAPI 3.0 / Swagger UI integrated at `/swagger-ui/index.html`. |
| **Layered Architecture** | Clean Architecture: API -> Domain -> Data Access. Domain has zero framework/DB imports. |
| **Data Access Layer** | JetBrains Exposed SQL DSL repository adapters connecting to PostgreSQL. |
| **Centralized Security** | Stateless JWT authentication enforced centrally via `JwtAuthenticationFilter`. |
| **Protected Endpoints** | Multiple protected `POST`, `GET`, and `DELETE` endpoints requiring Bearer tokens. |
| **Containerization** | Multi-stage `Containerfile` and `compose.yaml` supporting Docker and Podman. |
| **Load Testing** | Pre-configured k6 benchmark scripts for baseline measurement on Kaggle CPU. |

---

## 5. How to Run the Application

You can launch Kronyka using either **Docker / Podman** (all-in-one) or **Gradle** (local development).

### Option A: All-in-One via Docker or Podman (Recommended)
Build and run both the PostgreSQL database and Spring Boot application with a single command:

```bash
# Using Docker Compose
docker compose up --build

# Using Podman Compose
podman compose up --build
```
The application will be live at `http://localhost:8080`.

To stop the containers:
```bash
docker compose down
# or
podman compose down
```

---

### Option B: Local Development via Gradle

#### 1. Start the PostgreSQL Container
```bash
docker compose up -d postgres
# or with podman
podman compose up -d postgres
```

#### 2. Run the Application
```bash
./gradlew bootRun
```

#### 3. Run Automated Tests
```bash
./gradlew test
```

