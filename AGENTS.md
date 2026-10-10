# AGENTS.md - Core Rules & Invariants for Kronyka

## 1. Project Stack & Constraints

- **Language & Runtime**: Kotlin (2.4.x) on JDK 25 (Eclipse Temurin 25).
- **Framework**: Spring Boot (4.x).
- **Database**: PostgreSQL 18.
- **Persistence**: **JetBrains Exposed (1.5.x) SQL DSL exclusively**.
  - ⚠️ **Strict Constraint**: NEVER use Exposed DAO (`IntEntity`, `EntityClass`). Always use Exposed `Table` singletons and direct SQL DSL queries (`selectAll().where`, `insert`, `update`, `upsert`).
- **Currency**: All monetary values (VNĐ) MUST be stored as `Long` / `BIGINT`. Never use `Double` or `Float`.
- **Language**: All code, comments, rules, and documentation MUST be in **English**.

---

## 2. Non-Negotiable Architectural Invariants

1. **Clean Architecture Purity**:
   - `domain/`: **Zero framework imports**. No Spring annotations (`@Service`, `@Autowired`, etc.), no Exposed, no JDBC, no Web classes. Contains pure Kotlin entities, domain services, and repository ports.
   - `presentation/`: REST controllers, `@Valid` DTOs, and `@RestControllerAdvice`.
   - `infrastructure/`: Implements repository ports using Exposed SQL DSL, handles Spring configs and JWT security.
   - Dependency direction: `presentation -> domain <- infrastructure`.

2. **Concurrency Control (Dutch Auction)**:
   - "Buy Now" (`POST /api/auctions/{id}/buy-dutch`) MUST use optimistic locking (`version` check).
   - If version mismatches or auction is no longer `ACTIVE`, immediately throw an exception mapped to HTTP `409 Conflict`.

3. **Vickrey Cryptographic Verification**:
   - Commit phase: Store `blind_hash` and `lockup_deposit >= actual_bid`. Never leak `actual_bid`.
   - Reveal phase: Verify `SHA-256(actual_bid + "_" + secret_salt) == blind_hash` and `actual_bid <= lockup_deposit`.
   - On verification failure: Return HTTP `400 Bad Request` (`REVEAL_MISMATCH`). The bidder is permitted to retry until reveal phase ends.
   - Settle phase: Winner pays the 2nd highest bid price (or `reserve_price` if only 1 valid bid).

4. **Centralized Security**:
   - Stateless JWT tokens (`Authorization: Bearer <token>`).
   - Authentication MUST be handled centrally via Spring Security filter (`JwtAuthenticationFilter`), never duplicated inside controller methods.

5. **Exposed Upsert for Vickrey Bids**:
   - Enforce `UNIQUE(auction_id, bidder_id)` and use atomic upserts (`insert ... onConflict` / `upsert`) to prevent race conditions on repeat commitments.

6. **Layered Testing Conventions (Kotest + JUnit 5)**:
   - `domain/`: Tested exclusively with pure **Kotest Specs** (`BehaviorSpec`, `FunSpec`) and in-memory fake repositories. No Spring Boot context.
   - `presentation/` & `infrastructure/`: Tested with **JUnit 5 (`@Test`)** to maintain full compatibility with Spring test slices (`@WebMvcTest`, `@SpringBootTest`) and database rollback transactions.
   - Assertions: **Kotest Assertions (`shouldBe`, `shouldNotBeNull`, `shouldThrow`)** are used universally across ALL layers.

---

## 3. Detailed Specifications Index (`docs/llm/`)

When implementing or modifying features, consult the detailed specifications:
- **Architecture & Sequence Diagrams**: [docs/llm/ARCHITECTURE.md](docs/llm/ARCHITECTURE.md)
- **REST API Endpoints & Payloads**: [docs/llm/API_SPEC.md](docs/llm/API_SPEC.md)
- **Database Schema & DDL**: [docs/llm/DATABASE_SCHEMA.md](docs/llm/DATABASE_SCHEMA.md)
- **Domain Engines & Formulas**: [docs/llm/DOMAIN_ENGINES.md](docs/llm/DOMAIN_ENGINES.md)

---

## 4. Verification Commands

```bash
# Run unit & integration tests
./gradlew test

# Build application artifact
./gradlew bootJar -x test

# Spin up local database
docker compose up -d postgres
```
