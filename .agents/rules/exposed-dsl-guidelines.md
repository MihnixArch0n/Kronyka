---
trigger: model_decision
description: "Rules and best practices for writing database queries with JetBrains Exposed SQL DSL in Kronyka."
---

# JetBrains Exposed SQL DSL Guidelines

## 1. Core Rule: SQL DSL Only (No Exposed DAO)
- **Strictly Prohibited**: Do NOT use Exposed DAO classes (`EntityClass`, `IntEntity`, `UUIDEntity`). DAO pattern couples entity state to active database sessions and introduces hidden lazy-loading traps.
- **Mandatory Pattern**: Define database schemas as singleton `Table` objects (`org.jetbrains.exposed.sql.Table`) and execute queries using Exposed SQL DSL functions.

## 2. Table Definition Standards
Tables reside in `infrastructure/persistence/tables/`:

```kotlin
package example.kronyka.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : Table("users") {
    val id = uuid("id")
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val fullName = varchar("full_name", 100)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- Primary keys: Random UUID (`UUID.randomUUID()`) generated in code or defaults.
- Money fields: Always `long("start_price")`, representing Vietnamese Dong (VNĐ) as integer cents/units. Never use floating-point types.

## 3. Querying & Mapping to Domain Models
- Each repository adapter in `infrastructure/persistence/` should map `ResultRow` to pure domain models via private extension functions:
```kotlin
private fun ResultRow.toDomain(): User = User(
    id = this[UsersTable.id],
    username = this[UsersTable.username],
    passwordHash = this[UsersTable.passwordHash],
    fullName = this[UsersTable.fullName],
    createdAt = this[UsersTable.createdAt]
)
```
- Modern Exposed syntax: Prefer `AuctionsTable.selectAll().where { ... }` over legacy `select { ... }`.

## 4. Concurrency & Atomic Updates

### 4.1. Dutch Auction Optimistic Locking
To prevent race conditions when multiple buyers click "Buy Now" simultaneously:
```kotlin
val updatedRows = AuctionsTable.update({
    (AuctionsTable.id eq auctionId) and
    (AuctionsTable.status eq "ACTIVE") and
    (AuctionsTable.version eq expectedVersion)
}) {
    it[status] = "SOLD"
    it[winnerId] = buyerId
    it[finalPrice] = purchasedPrice
    it[version] = expectedVersion + 1
}

if (updatedRows == 0) {
    throw ConcurrencyConflictException("Auction has already been purchased by another bidder.")
}
```

### 4.2. Upsert for Vickrey Bids
Because `vickrey_bids` has `UNIQUE(auction_id, bidder_id)`:
```kotlin
VickreyBidsTable.upsert(
    keys = arrayOf(VickreyBidsTable.auctionId, VickreyBidsTable.bidderId)
) {
    it[id] = UUID.randomUUID()
    it[auctionId] = bid.auctionId
    it[bidderId] = bid.bidderId
    it[blindHash] = bid.blindHash
    it[lockupDeposit] = bid.lockupDeposit
    it[createdAt] = Instant.now()
}
```
This safely overwrites previous commitments before the commit phase closes, eliminating race condition errors.
