# Kronyka: Dual-Engine Auction Platform

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-green.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Exposed](https://img.shields.io/badge/Exposed-1.5.0-purple.svg)](https://github.com/JetBrains/Exposed)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg?logo=postgresql)](https://www.postgresql.org)
[![Java 25](https://img.shields.io/badge/JDK-25%20(Temurin)-orange.svg?logo=openjdk)](https://adoptium.net)
[![Container](https://img.shields.io/badge/Container-Docker%20%7C%20Podman-blue.svg?logo=docker)](https://www.docker.com)

**Kronyka** is a high-concurrency, game-theoretic auction service built with **Kotlin 2.4**, **Spring Boot 4.1** (with Java 25 Virtual Threads), and **JetBrains Exposed SQL DSL**, strictly adhering to **Clean Architecture (Hexagonal / Ports & Adapters)**.

This repository serves as the **Phase 1 baseline implementation** of an auction backend system, providing a robust functional foundation while leaving observable architectural characteristics and bottlenecks for **Phase 2 Quality Attribute analysis** (performance, concurrency, and scalability improvements).

---

## 1. Problem Statement & Core Business Mechanics

### 1.1. Problem Statement
Conventional open ascending-bid auctions (English auctions) suffer from two fundamental market failures:
- **Last-second Bid Sniping**: Automated bots place bids in the terminal seconds, denying organic price discovery and degrading fair participation.
- **Shill Bidding & Collusion**: Unscrupulous sellers or insiders artificially inflate prices using transparent bid visibility.

Kronyka eliminates these vulnerabilities by implementing two complementary, non-standard auction mechanisms:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           KRONYKA DUAL-ENGINE PLATFORM                          │
├──────────────────────────────────────┬──────────────────────────────────────────┤
│ 1. Vickrey Sealed-Bid Engine         │ 2. Step-Decay Dutch Engine               │
│ - Dominant strategy: Truthful bidding│ - Fast liquidation / dynamic pricing     │
│ - 3-Phase State Machine              │ - Memory-evaluated staircase formula     │
│ - Cryptographic SHA-256 Blind Bids   │ - Optimistic Concurrency Control (OCC)   │
│ - Second-Price Settlement Rule       │ - Immediate 409 Conflict for race losers │
└──────────────────────────────────────┴──────────────────────────────────────────┘
```

### 1.2. Business Workflows & Engine Rules

#### Engine 1: Vickrey Sealed-Bid Blind Auction (Nobel Prize in Economics 1996)
Operates as a strict 3-phase finite state machine:
1. **Commit Phase (`COMMIT`)**:
   - Bidders submit a commitment: `blind_hash = SHA-256(actual_bid + "_" + secret_salt)` alongside a `lockup_deposit` where `lockup_deposit >= actual_bid`.
   - No bid amount is leaked into the database during this phase. Atomic upserts (`UNIQUE(auction_id, bidder_id)`) prevent race conditions.
2. **Reveal Phase (`REVEAL`)**:
   - Bidders disclose their `actual_bid` and `secret_salt`.
   - The engine validates:
     - `SHA-256(actual_bid + "_" + secret_salt) == blind_hash`
     - `actual_bid <= lockup_deposit`
   - Mismatches return HTTP `400 Bad Request` (`REVEAL_MISMATCH`). The bidder may retry until the reveal window expires.
3. **Settlement Phase (`SETTLED`)**:
   - Automatically executed by a scheduled background worker (`@Scheduled`).
   - **Winner**: The bidder with the highest verified bid.
   - **Payable Price**: The **second-highest bid** (or `reserve_price` if only one valid bid was submitted).

#### Engine 2: Step-Decay Dutch Auction
- A descending-price mechanism for high-velocity liquidation.
- **Price Formula**: Evaluated purely in memory from clock time (no cron or mutation required):
  ```
  P(t) = max(floor_price, start_price - floor((t - start_time) / step_interval) * step_decrement)
  ```
- **Concurrency & Race Condition ("Buy Now")**:
  - Purchasing (`POST /api/v1/auctions/{id}/buy-dutch`) executes with an **Optimistic Locking check** on `version`.
  - The first transaction to commit captures the item and transitions status to `SOLD`. Late concurrent requests immediately receive HTTP `409 Conflict`.

#### Centralized Security & Authentication
- Stateless JWT authentication via `JwtAuthenticationFilter` (HMAC-SHA256).
- Endpoints require authentication where appropriate (e.g., `POST /api/v1/auctions`, `POST /api/v1/auctions/{id}/commit`, `DELETE /api/v1/auctions/{id}`). Authentication is handled strictly in the filter chain, never duplicated inside controller methods.

---

## 2. Project Architecture & Code Organization

Kronyka strictly enforces **Clean Architecture** (Ports & Adapters) boundaries with unidirectional dependencies pointing inward:

```
src/main/kotlin/example/kronyka/
├── domain/                         # 1. CORE DOMAIN (Zero framework/library imports)
│   ├── model/                      #    Entities (Auction, Bid, User), Value Objects, Enums
│   ├── repository/                 #    Repository Ports (Pure Kotlin interfaces)
│   └── service/                    #    Domain Engines (VickreyAuctionEngine, DutchAuctionEngine)
│
├── presentation/                   # 2. PRESENTATION LAYER (Inbound Adapters)
│   ├── controller/                 #    REST Controllers (AuctionController, AuthController)
│   ├── dto/                        #    Request/Response DTOs & Jakarta Validation (@Valid)
│   └── advice/                     #    Global Exception Handling (@RestControllerAdvice, RFC 7807)
│
└── infrastructure/                 # 3. INFRASTRUCTURE LAYER (Outbound Adapters)
    ├── persistence/                #    JetBrains Exposed SQL DSL Tables & Repository Implementations
    ├── security/                   #    Centralized Spring Security & JwtAuthenticationFilter
    ├── scheduler/                  #    Scheduled Background Settlement Worker
    └── config/                     #    Spring Boot & Database Configuration
```

### Architectural Invariants:
- **`domain/` Isolation**: Pure Kotlin only. Does not import Spring (`@Service`, `@Autowired`), JDBC, or Exposed libraries. Tested exclusively with pure Kotest specs.
- **Exposed SQL DSL Exclusivity**: Uses Exposed `Table` singletons and explicit SQL DSL (`selectAll().where`, `insert`, `upsert`, `update`). Exposed DAO (`EntityClass`, `IntEntity`) is strictly prohibited.
- **Currency Invariant**: All monetary values (VNĐ) are stored and manipulated as `Long` / `BIGINT`. Floating-point arithmetic (`Double`/`Float`) is forbidden.

---

## 3. Known Limitations & Baseline Constraints

Following the Phase 1 specification, Kronyka focuses on a functional, unoptimized baseline service. Key observable characteristics and architectural limitations include:

- **Direct Database Reads**: Every listing query (`GET /api/v1/auctions`) and Dutch price computation (`GET /api/v1/auctions/{id}/price`) queries PostgreSQL directly without an in-memory or distributed caching layer.
- **Client-Side Polling**: Clients must poll the price endpoint repeatedly to track Dutch auction price decay, as real-time push streams are not implemented.
- **Scheduled Settlement Polling**: Vickrey auction settlement relies on a periodic `@Scheduled` polling task scanning the database every 5,000ms rather than event-driven notifications.
- **Optimistic Concurrency Contention**: Dutch auction "Buy Now" requests rely on Optimistic Concurrency Control (`version`), causing high contention and HTTP `409 Conflict` bursts under concurrent load.
- **Monolithic In-Process Topology**: All domain engines, scheduler, and API endpoints execute within a single containerized application process.
- **Single-Node Settlement Scheduling & Scalability Bottleneck**: The background settlement worker runs as an uncoordinated in-process loop without distributed locking or leader election, preventing horizontal multi-instance scale-out and relying on sequential blocking batch execution.
- **Absence of Ingress Traffic Shaping & Resilience**: The API lacks rate limiting and circuit breaking mechanisms, leaving the service vulnerable to traffic spikes, request flooding, and cascading failures under database saturation.

---

## 4. Technical Requirements & Deployment

### 4.1. Prerequisites & Environment
- **JDK**: Eclipse Temurin 25 (Java 25).
- **Container Engine**: Docker Engine 24+ (with Compose v2) or Podman 4.5+ (with podman-compose).
- **Database**: PostgreSQL 18 (Alpine image included in `compose.yaml`).

### 4.2. Environment Configuration
Default configuration variables are declared in `.env.example` and wired through `compose.yaml` / `application.yaml`:

| Environment Variable | Default Value | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port exposed by the Spring Boot service |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/kronyka` | PostgreSQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `kronyka` | PostgreSQL database username |
| `SPRING_DATASOURCE_PASSWORD` | `kronyka_secret` | PostgreSQL database password |
| `JWT_SECRET` | *(HMAC-SHA256 32-char string)* | Symmetric key for signing JWT tokens |
| `JWT_EXPIRATION` | `86400` | JWT token validity duration (seconds) |
| `KRONYKA_AUCTION_SETTLEMENT_FIXED_DELAY_MS` | `5000` | Background settlement scan interval |

---

## 5. Build, Run & Verification Guide

### 5.1. Option A: Run via Docker / Podman (Recommended for Phase 2)
The container build utilizes a **Multi-Stage Containerfile** with **Java 25 Ahead-Of-Time (AOT) Cache training** (`-XX:AOTCacheOutput=app.aot`) to drastically reduce JVM cold-start latency and resident memory footprint:

```bash
# Build and start PostgreSQL + Kronyka App
docker compose up --build

# Or with Podman
podman compose up --build
```
- App will be healthy and live at: `http://localhost:8080`
- Interactive OpenAPI / Scalar UI: `http://localhost:8080/scalar`
- Health check endpoint: `http://localhost:8080/actuator/health`

To stop the containers:
```bash
docker compose down -v
```

---

### 5.2. Option B: Local Development via Gradle
For day-to-day feature development:

```bash
# 1. Start only the PostgreSQL database container
docker compose up -d postgres

# 2. Run the Spring Boot application (requires JDK 25)
./gradlew bootRun

# 3. Run Unit and Integration tests (Kotest + JUnit 5)
./gradlew test
```

---

## 6. Performance Benchmarking & Load Testing (Phase 1 Baseline)

### 6.1. Test Environment Specification
- **Hardware & Host**: Kaggle Cloud Container - 4 vCPUs, 30 GB RAM.
- **Runtimes & Stack**: Eclipse Temurin OpenJDK 25 (Java 25 with Virtual Threads enabled), Spring Boot 4.1.1, PostgreSQL 18.
- **Database Driver**: JetBrains Exposed 1.5.0 SQL DSL with default HikariCP connection pool.

---

### 6.2. Macro System Performance Comparison (20 vs 100 vs 500 vs 1000 VUs)

| Load Level | Virtual Users | Total Requests (1 min) | Throughput (RPS) | Latency Median | Latency Avg | Latency P95 | Latency P99 | Assertion Success Rate |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Baseline Load** | **20 VUs** | 6,300 | **101.6 req/s** | 30.61 ms | 162.84 ms | **721.54 ms** | 1.15 s | **100.00%** (3,780 / 3,780) |
| **Normal High Load** | **100 VUs** | 7,556 | **118.8 req/s** | 370.00 ms | 796.85 ms | **2.95 s** | 5.33 s | **100.00%** (4,532 / 4,532) |
| **Stress Load** | **500 VUs** | 8,498 | **115.5 req/s** | 466.07 ms | 4.11 s | **20.68 s** | 40.41 s | **99.67%** (4,964 / 4,980) |
| **Saturation Limit** | **1000 VUs** | 5,382 | **59.8 req/s** | 1.29 s | 13.72 s | **59.78 s** | 60.00 s | **84.96%** (2,786 / 3,279) |

---

### 6.3. Granular Per-Endpoint Latency Breakdown

| # | Endpoint | 20 VUs (Avg / P95) | 100 VUs (Avg / P95) | 500 VUs (Avg / P95) | 1000 VUs (Avg / P95) |
| :---: | :--- | :---: | :---: | :---: | :---: |
| **1** | `/api/auth/register` | 634.3 ms / 1.34 s | 2.01 s / 6.64 s | 17.38 s / 49.17 s | 33.08 s / 59.91 s |
| **2** | `/api/auth/login` | 408.2 ms / 921.1 ms | 1.07 s / 4.14 s | 7.80 s / 30.61 s | 16.05 s / 59.40 s |
| **3** | `/api/auth/me` | 113.1 ms / 366.6 ms | 551.2 ms / 2.57 s | 2.73 s / 14.16 s | 5.79 s / 30.03 s |
| **4** | `/api/auctions` | 132.5 ms / 435.9 ms | 699.5 ms / 2.58 s | 3.97 s / 16.26 s | 6.97 s / 30.14 s |
| **5** | `/api/auctions` (Vickrey) | 72.6 ms / 257.3 ms | 619.6 ms / 2.41 s | 3.36 s / 15.38 s | 6.32 s / 30.26 s |
| **6** | `/api/auctions/{id}` | 54.0 ms / 210.4 ms | 663.6 ms / 2.50 s | 2.67 s / 12.98 s | 7.26 s / 30.37 s |
| **7** | `/api/auctions/{id}/commit` | 41.3 ms / 131.1 ms | 549.7 ms / 2.16 s | 2.12 s / 9.46 s | 5.94 s / 30.17 s |
| **8** | `/api/auctions/{id}/reveal` | 21.6 ms / 72.2 ms | 642.7 ms / 2.80 s | 2.44 s / 11.51 s | 5.00 s / 22.71 s |
| **9** | `/api/auctions/{id}` (Cancel) | 22.0 ms / 55.2 ms | 641.4 ms / 2.75 s | 1.08 s / 4.73 s | 10.80 s / 30.56 s |
| **10**| `/api/auctions/dutch` | 15.8 ms / 34.9 ms | 640.2 ms / 2.34 s | 2.49 s / 9.19 s | 12.95 s / 30.23 s |
| **11**| `/api/auctions/{id}/price` | **8.1 ms / 20.9 ms** | **526.9 ms / 2.44 s** | **1.36 s / 5.89 s** | **2.24 s / 10.60 s** |
| **12**| `/api/auctions/{id}/buy-dutch` | **14.5 ms / 30.5 ms** | **474.7 ms / 1.98 s** | **1.17 s / 5.12 s** | **2.72 s / 12.87 s** |

---

## 7. Detailed Documentation Index

For in-depth architectural specifications and implementation details, refer to:
- 🏛️ **System Architecture & Sequence Flows**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Layer boundaries, sequence diagrams, and concurrency control.
- 🗄️ **Database Schema & DDL**: [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) — Tables (`users`, `auctions`, `vickrey_bids`), data types, constraints, and indexes.
- ⚙️ **Domain Engines & Formulas**: [docs/llm/DOMAIN_ENGINES.md](docs/llm/DOMAIN_ENGINES.md) — Mathematical proofs, pricing formulas, and verification steps.
- ⚡ **Interactive API Contract (Scalar)**: Run the app and browse to `http://localhost:8080/scalar` (or OpenAPI JSON at `/v3/api-docs`).
