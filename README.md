# Kronyka: Dual-Engine Auction Platform

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-green.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Exposed](https://img.shields.io/badge/Exposed-1.5.0-purple.svg)](https://github.com/JetBrains/Exposed)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg?logo=postgresql)](https://www.postgresql.org)
[![Java 25](https://img.shields.io/badge/JDK-25%20(Temurin)-orange.svg?logo=openjdk)](https://adoptium.net)
[![Docker](https://img.shields.io/badge/Container-Docker%20%7C%20Podman-blue.svg?logo=docker)](https://www.docker.com)

🌐 **Language**: [English (Current)](README.md) | [🇻🇳 Tiếng Việt](README.vi.md)

**Kronyka** is an auction backend service built with **Kotlin 2.4**, **Spring Boot 4.1** (using Java 25 Virtual Threads), and **JetBrains Exposed SQL DSL**, structured around **Clean Architecture**.

This repository is the **Phase 1** foundation: it implements the core auction rules and leaves a clear baseline for **Phase 2** teams to benchmark, profile, and optimize performance and concurrency.

---

## 1. Problem & Core Auction Mechanics

Traditional ascending auctions (English auctions) have two common problems:
1. **Last-second Sniping**: Bots wait until the last 1–2 seconds to place bids, leaving regular users no time to react.
2. **Shill Bidding**: Sellers or insiders place fake bids to push prices up because all bids are visible.

Kronyka solves this by supporting two different auction styles:

| Auction Type | How it Works | Price & Winner Calculation | Concurrency & Settlement |
|---|---|---|---|
| **Vickrey (Sealed-Bid)** | 3 steps: commit hidden bid (`COMMIT`) $\to$ reveal bid (`REVEAL`) $\to$ settle (`SETTLED`). | Bidders submit a hidden hash SHA256({bid}_{salt}) with a deposit. When time expires, the highest bidder wins, but **only pays the 2nd highest bid**. This encourages people to bid their true value. | A background scheduler periodically scans ended auctions to pick the winner and refund deposits. |
| **Dutch Auction** | Price starts high and drops by a set amount at fixed time intervals. | Current price is calculated from system time using a simple formula. The first person to accept the price and click buy wins immediately. | Uses optimistic locking (`version` column). The first request wins the item; later requests immediately receive `409 Conflict`. |

> 📖 **Formulas & Sequence Diagrams**: See [docs/DOMAIN_ENGINES.md](docs/DOMAIN_ENGINES.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 2. Architecture & Code Structure

The code follows Clean Architecture with dependencies pointing inward:

```
src/main/kotlin/example/kronyka/
├── domain/                  # 1. CORE DOMAIN (Pure Kotlin, NO framework imports)
│   ├── model/               #    Entities (Auction, Bid, User)
│   ├── repository/          #    Repository interfaces (pure Kotlin)
│   └── service/             #    Auction business logic (Vickrey, Dutch)
├── presentation/            # 2. PRESENTATION (Handles HTTP requests)
│   ├── controller/          #    REST Controllers (Auction, Auth)
│   ├── dto/                 #    Request/Response DTOs & Validation (@Valid)
│   └── advice/              #    Global error handling (@RestControllerAdvice)
└── infrastructure/          # 3. INFRASTRUCTURE (External connections)
    ├── persistence/         #    Database tables & queries using Exposed SQL DSL
    ├── security/            #    JWT filter & Spring Security setup
    ├── scheduler/           #    Background worker for auction settlement
    └── config/              #    App and OpenAPI configuration
```

### 3 Key Coding Rules:
1. **Keep `domain/` pure**: Write only in standard Kotlin. Do not use Spring annotations (`@Service`, `@Autowired`), Exposed, or JDBC imports in this package.
2. **Use Exposed SQL DSL only**: Query tables using singleton `Table` objects and DSL functions (`selectAll`, `insert`, `upsert`, `update`). Do not use Exposed DAO (`EntityClass`, `IntEntity`).
3. **Store money (VNĐ) as `Long`**: Never use `Double` or `Float` for currency to prevent rounding errors.

---

## 3. Known Issues for Phase 2

The Phase 1 service focuses on functional correctness and deliberately leaves these areas for Phase 2 teams to improve:

1. **Direct Database Reads**: Every request for listings or current Dutch prices hits PostgreSQL directly. There is no cache (like Redis) yet.
2. **Client Polling**: Clients have to call the API repeatedly to track price drops instead of receiving push updates via WebSockets or Server-Sent Events.
3. **High Contention on "Buy Now"**: Dutch auctions check `version` on purchase. When many people buy in the same second, only one succeeds and the rest get `409 Conflict`.
4. **Single-Node Settlement Worker**: The Vickrey settlement loop runs inside the application via `@Scheduled` without distributed locks (like ShedLock or Redis). Running multiple instances will cause overlapping scans.
5. **No Rate Limiting**: There is no rate limiter or circuit breaker yet, so large traffic spikes can exhaust the database connection pool.

---

## 4. Technical Requirements & How to Run

### 4.1. Requirements
- **JDK**: Eclipse Temurin 25 (Java 25)
- **Container**: Docker or Podman
- **Database**: PostgreSQL 18 (configured in `compose.yaml`)

### 4.2. Run with Docker / Podman (Recommended)
The project includes a multi-stage `Containerfile` that uses Java 25 AOT Cache to speed up startup:

```bash
# Build and run PostgreSQL + the application
docker compose up --build -d

# Check application logs
docker compose logs -f app

# Check container status
docker compose ps
```
- App URL: `http://localhost:8080`
- Health check: `http://localhost:8080/actuator/health`

### 4.3. Run Locally with Gradle
```bash
# 1. Start PostgreSQL only
docker compose up -d postgres

# 2. Run the application (requires JDK 25 installed)
./gradlew bootRun

# 3. Run all tests
./gradlew test
```

> 📖 **Detailed deployment & configuration guide**: See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

---

## 5. API Documentation & Project Docs

### 5.1. API Specification
- **Interactive Web UI (Scalar)**: Open `http://localhost:8080/scalar` while the app is running (or get raw JSON at `http://localhost:8080/v3/api-docs`).
- **Downloadable Specs (.json / .yaml)**: [GitHub Releases](https://github.com/MihnixArch0n/Kronyka/releases).

### 5.2. Detailed Documents
- 🏛️ **[System Architecture](docs/ARCHITECTURE.md)**: Layer design and sequence flow diagrams.
- 🗄️ **[Database Schema](docs/DATABASE_SCHEMA.md)**: PostgreSQL tables, columns, and keys.
- ⚙️ **[Auction Engine Details](docs/DOMAIN_ENGINES.md)**: Formulas, validation steps, and settlement rules.
- 🚀 **[Deployment Guide](docs/DEPLOYMENT.md)**: Containerfile details, Java 25 AOT setup, and environment variables.

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

