# System Architecture

🌐 **Language**: [English (Current)](ARCHITECTURE.md) | [🇻🇳 Tiếng Việt](ARCHITECTURE.vi.md)

Kronyka is organized into 3 layers following **Clean Architecture** (dependencies point inward):

```
example.kronyka/
├── domain/                  # 1. CORE DOMAIN (Pure Kotlin)
│   ├── model/               #    Entities, value objects, enums, exceptions
│   ├── repository/          #    Repository interfaces (pure Kotlin, no framework code)
│   └── service/             #    Auction engines (Vickrey, Dutch)
├── presentation/            # 2. PRESENTATION (Handles HTTP web requests)
│   ├── controller/          #    REST Controllers
│   ├── dto/                 #    Request/Response DTOs and validation rules
│   └── advice/              #    Global error handlers (@RestControllerAdvice)
└── infrastructure/          # 3. INFRASTRUCTURE (Database & external services)
    ├── persistence/         #    Database tables and queries (Exposed SQL DSL)
    ├── security/            #    JWT authentication filter and Spring Security
    ├── scheduler/           #    Background worker for auction settlement
    └── config/              #    Spring Boot, database, and OpenAPI setup
```

### Dependency Rule
Dependencies flow **strictly inward**: `Presentation -> Domain <- Infrastructure`
- **Domain**: Pure business rules written in Kotlin. Never imports Spring, Exposed, JDBC, or web annotations.
- **Presentation**: Accepts HTTP requests, validates inputs, calls domain services, and returns JSON responses.
- **Infrastructure**: Implements repository interfaces using Exposed SQL DSL, manages database connections, and sets up security.

---

## 1. Sequence Diagrams

### 1.1. Vickrey Sealed-Bid Auction

```mermaid
sequenceDiagram
    autonumber
    actor Bidder
    participant Controller as Presentation (VickreyController)
    participant Engine as Domain (VickreyAuctionEngine)
    participant Repo as Infrastructure (ExposedVickreyBidRepository)
    participant DB as PostgreSQL

    Note over Bidder: Step 1: COMMIT (Secret bid)
    Bidder->>Controller: POST /api/auctions/{id}/commit {blind_hash, lockup_deposit}
    Controller->>Engine: submitCommitment(auctionId, bidderId, blindHash, deposit)
    Engine->>Repo: upsertCommitment(bidRecord)
    Repo->>DB: INSERT ... ON CONFLICT (auction_id, bidder_id) DO UPDATE
    DB-->>Repo: Saved
    Repo-->>Engine: Saved Record
    Engine-->>Controller: BidResult
    Controller-->>Bidder: 201 Created

    Note over Bidder: Step 2: REVEAL (Open the bid)
    Bidder->>Controller: POST /api/auctions/{id}/reveal {actual_bid, secret_salt}
    Controller->>Engine: revealBid(auctionId, bidderId, actualBid, salt)
    Engine->>Engine: Verify SHA256(actualBid + "_" + salt) == blindHash
    alt Hash mismatch or actualBid > deposit
        Engine-->>Controller: Throw RevealMismatchException
        Controller-->>Bidder: 400 Bad Request (REVEAL_MISMATCH - Retry allowed)
    else Valid
        Engine->>Repo: markRevealed(auctionId, bidderId, actualBid)
        Repo->>DB: UPDATE vickrey_bids SET is_revealed=true, actual_bid=...
        DB-->>Repo: OK
        Repo-->>Engine: OK
        Engine-->>Controller: RevealResult
        Controller-->>Bidder: 200 OK
    end
```

### 1.2. Dutch Auction ("Buy Now" Race)

```mermaid
sequenceDiagram
    autonumber
    actor BuyerA as Buyer A (First)
    actor BuyerB as Buyer B (Slightly late)
    participant Controller as Presentation (DutchController)
    participant Engine as Domain (DutchAuctionEngine)
    participant Repo as Infrastructure (ExposedAuctionRepository)
    participant DB as PostgreSQL

    BuyerA->>Controller: POST /api/auctions/{id}/buy-dutch
    BuyerB->>Controller: POST /api/auctions/{id}/buy-dutch

    Controller->>Engine: buyNow(auctionId, buyerA)
    Engine->>Engine: Calculate current price in memory
    Engine->>Repo: atomicPurchase(auctionId, buyerA, price, expectedVersion)
    Repo->>DB: UPDATE auctions SET status='SOLD', winner_id=A, version=v+1 WHERE version=v
    DB-->>Repo: 1 row affected
    Repo-->>Engine: Success
    Engine-->>Controller: PurchaseResult
    Controller-->>BuyerA: 200 OK (Bought!)

    Controller->>Engine: buyNow(auctionId, buyerB)
    Engine->>Engine: Calculate current price in memory
    Engine->>Repo: atomicPurchase(auctionId, buyerB, price, expectedVersion)
    Repo->>DB: UPDATE auctions SET status='SOLD', winner_id=B, version=v+1 WHERE version=v
    DB-->>Repo: 0 rows affected (version already incremented)
    Repo-->>Engine: 0 rows affected
    Engine-->>Controller: Throw ConcurrencyConflictException
    Controller-->>BuyerB: 409 Conflict (Already sold)
```

---

## 2. Concurrency & Database Rules

1. **Optimistic Locking (`version`)**:
   - The Dutch engine checks `version` when someone clicks buy. If multiple people buy at the exact same time, only the first request updates the row; the others get `409 Conflict`.
2. **Safe Updates for Vickrey Commitments**:
   - `UNIQUE(auction_id, bidder_id)` allows bidders to update their deposit safely without creating duplicate bids or race conditions.
3. **Stateless Price Calculation**:
   - The Dutch price is computed directly from time in memory. There is no background job needed to update prices in the database every second.

---

## 3. Testing Strategy

| Layer | Framework | Style | Assertions | How it runs |
|---|---|---|---|---|
| **Domain** | **Kotest** | `BehaviorSpec`, `FunSpec` | `shouldBe`, `shouldThrow` | Uses in-memory fake repositories. No Spring context or database needed (runs in milliseconds). |
| **Presentation** | **JUnit 5** | `@Test`, `@WebMvcTest` | `shouldBe`, `shouldNotBeNull` | Spring `MockMvc` + MockK to test controllers and validation in isolation. |
| **Infrastructure** | **JUnit 5** | `@Test` | `shouldBe`, `shouldHaveSize` | In-memory database with transaction rollback to verify SQL queries. |
