# System Architecture & Technical Design

Kronyka is designed as a decoupled, layered backend service following **Clean Architecture** (Hexagonal / Ports & Adapters) principles. This ensures business domain independence from the web framework, persistence layer, and external libraries.

---

## 1. Architectural Layers & Separation of Concerns

```
example.kronyka/
│
├── domain/                                  # 1. DOMAIN LAYER (Pure Business Core)
│   ├── model/                               # Entities, value objects, enums, exceptions
│   ├── repository/                          # Repository ports (pure Kotlin interfaces)
│   └── service/                             # Domain engines & use case workflows
│
├── presentation/                            # 2. PRESENTATION LAYER (Inbound Web Adapter)
│   ├── controller/                          # Spring REST Controllers (JSON API)
│   ├── dto/                                 # Request/Response DTOs & Validation
│   └── advice/                              # Global exception handlers & RFC error models
│
└── infrastructure/                          # 3. INFRASTRUCTURE LAYER (Outbound Adapters)
    ├── persistence/                         # Exposed SQL DSL tables & repository adapters
    ├── security/                            # Centralized JWT authentication filter & config
    └── config/                              # Spring Boot & Exposed database configuration
```

### Layer Dependency Rule
Dependencies flow **strictly inward**:
`Presentation -> Domain <- Infrastructure`

- **Domain Layer**:
  - Independent of Spring Web, Exposed SQL, JDBC, or any framework annotations.
  - Fully unit-testable without database or web container mocks.
- **Presentation Layer**:
  - Exposes REST endpoints conforming to HTTP/JSON standards.
  - Applies input validation (`@Valid`, Jakarta constraints) and maps domain exceptions to standard HTTP status codes.
- **Infrastructure Layer**:
  - Implements the repository ports defined in `domain/repository/` using JetBrains Exposed SQL DSL queries.
  - Manages database transactions, connections, and security middleware.

---

## 2. Interaction & Sequence Flows

### 2.1. Vickrey Sealed-Bid Engine (3-Phase Commit-Reveal)

```mermaid
sequenceDiagram
    autonumber
    actor Bidder
    participant Controller as Presentation (VickreyController)
    participant Engine as Domain (VickreyAuctionEngine)
    participant Repo as Infrastructure (ExposedVickreyBidRepository)
    participant DB as PostgreSQL

    Note over Bidder: Phase 1: COMMIT (blind_hash = SHA256(bid + "_" + salt))
    Bidder->>Controller: POST /api/auctions/{id}/commit {blind_hash, lockup_deposit}
    Controller->>Engine: submitCommitment(auctionId, bidderId, blindHash, deposit)
    Engine->>Repo: upsertCommitment(bidRecord)
    Repo->>DB: INSERT ... ON CONFLICT (auction_id, bidder_id) DO UPDATE
    DB-->>Repo: Saved
    Repo-->>Engine: Saved Record
    Engine-->>Controller: BidResult
    Controller-->>Bidder: 201 Created

    Note over Bidder: Phase 2: REVEAL (Send actual_bid & secret_salt)
    Bidder->>Controller: POST /api/auctions/{id}/reveal {actual_bid, secret_salt}
    Controller->>Engine: revealBid(auctionId, bidderId, actualBid, salt)
    Engine->>Engine: Verify SHA256(actualBid + "_" + salt) == blindHash
    alt Hash Mismatch or actualBid > deposit
        Engine-->>Controller: Throw RevealMismatchException
        Controller-->>Bidder: 400 Bad Request (REVEAL_MISMATCH - Retry Allowed)
    else Hash Valid
        Engine->>Repo: markRevealed(auctionId, bidderId, actualBid)
        Repo->>DB: UPDATE vickrey_bids SET is_revealed=true, actual_bid=...
        DB-->>Repo: OK
        Repo-->>Engine: OK
        Engine-->>Controller: RevealResult
        Controller-->>Bidder: 200 OK
    end
```

### 2.2. Step-Decay Dutch Auction (Concurrency Control)

```mermaid
sequenceDiagram
    autonumber
    actor BuyerA as Buyer A (Wins Race)
    actor BuyerB as Buyer B (Loses Race)
    participant Controller as Presentation (DutchController)
    participant Engine as Domain (DutchAuctionEngine)
    participant Repo as Infrastructure (ExposedAuctionRepository)
    participant DB as PostgreSQL

    BuyerA->>Controller: POST /api/auctions/{id}/buy-dutch
    BuyerB->>Controller: POST /api/auctions/{id}/buy-dutch
    Controller->>Engine: buyNow(auctionId, buyerA)
    Engine->>Engine: Calculate P(t) via stateless formula
    Engine->>Repo: atomicPurchase(auctionId, buyerA, price, expectedVersion)
    Repo->>DB: UPDATE auctions SET status='SOLD', winner_id=A, version=v+1 WHERE version=v
    DB-->>Repo: 1 row affected
    Repo-->>Engine: Success
    Engine-->>Controller: PurchaseResult
    Controller-->>BuyerA: 200 OK {status: "SOLD", winner: "Buyer A"}

    Controller->>Engine: buyNow(auctionId, buyerB)
    Engine->>Engine: Calculate P(t) via stateless formula
    Engine->>Repo: atomicPurchase(auctionId, buyerB, price, expectedVersion)
    Repo->>DB: UPDATE auctions SET status='SOLD', winner_id=B, version=v+1 WHERE version=v
    DB-->>Repo: 0 rows affected (version mismatch)
    Repo-->>Engine: 0 rows affected
    Engine-->>Controller: Throw ConcurrencyConflictException
    Controller-->>BuyerB: 409 Conflict {"error": "AUCTION_ALREADY_SOLD"}
```

---

## 3. Concurrency & Integrity Mechanisms

1. **Optimistic Locking (`version`)**:
   - The Dutch engine leverages optimistic concurrency control directly in SQL to serialize competing "Buy Now" requests without locking read queries.
2. **Atomic Upserts for Vickrey Commitments**:
   - A unique constraint on `(auction_id, bidder_id)` allows bidders to update their deposit commitments safely without race conditions.
3. **Stateless Step Math**:
   - Price calculation is completely functional and stateless in memory, removing background timer updates and database write amplification.
