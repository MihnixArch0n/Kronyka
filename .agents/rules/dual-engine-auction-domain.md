---
trigger: model_decision
description: "Domain logic, mathematical formulas, and business invariants for both Vickrey and Dutch auction engines."
---

# Dual-Engine Auction Domain Rules

## 1. Engine 1: Vickrey Sealed-Bid Blind Auction

### 1.1. Lifecycle & State Machine
The Vickrey auction transitions strictly through 3 distinct time-gated phases:
```
[COMMIT]  -- (time >= commit_end_time) -->  [REVEAL]  -- (time >= reveal_end_time & manual/worker settle) -->  [SETTLED]
```

### 1.2. Phase 1: Commit Phase
- **Time Window**: `start_time` to `commit_end_time`.
- **Bidder Input**:
  - `blind_hash`: 64-character lowercase hexadecimal SHA-256 string.
  - `lockup_deposit`: Monetary amount where `lockup_deposit >= actual_bid`.
- **Privacy Rule**: During COMMIT phase, the server and other participants MUST NOT know the actual bid. Information leaks are strictly prevented.
- **Repeat Bids**: If the same bidder submits a new commitment before `commit_end_time`, the system updates/upserts their existing commitment record.

### 1.3. Phase 2: Reveal Phase
- **Time Window**: `commit_end_time` to `reveal_end_time`.
- **State Check**: Auction must be in `REVEAL` state. New commitments are rejected.
- **Bidder Input**: `{ "actual_bid": Long, "secret_salt": String }`.
- **Server Verification Algorithm**:
  1. Compute hash: `computed_hash = SHA-256(actual_bid + "_" + secret_salt)`
  2. Verify: `computed_hash == committed_blind_hash`
  3. Verify: `actual_bid <= committed_lockup_deposit`
- **Verification Failure**:
  - If hash does not match, salt is invalid, or actual bid exceeds deposit, throw `RevealMismatchException` (mapped to HTTP `400 Bad Request`).
  - **Crucial Rule**: The bidder is allowed to retry multiple times until the reveal phase closes. Do not disqualify or delete their record on mismatch.
- **Verification Success**:
  - Mark `is_revealed = true`, save `actual_bid` and timestamp `revealed_at`.

### 1.4. Phase 3: Settle Phase (Second-Price Rule)
- **Trigger**: Called by the seller or automated job after `reveal_end_time`.
- **Calculation Logic**:
  1. Fetch all bids for the auction where `is_revealed == true`.
  2. Sort by `actual_bid` descending.
  3. **Case 1: Multiple valid bidders (>= 2)**:
     - Winner: 1st highest bidder.
     - Final Price (`P_pay`): 2nd highest bid value.
     - Second bidder ID recorded for audit trail (`second_bidder_id`).
  4. **Case 2: Exactly 1 valid bidder**:
     - Winner: The single bidder.
     - Final Price (`P_pay`): Auction `reserve_price`.
  5. **Case 3: No valid revealed bids**:
     - Status becomes `SETTLED` (or `EXPIRED_UNSOLD`) with `winner_id = null`, `final_price = null`.
- **Deposit Refunds**:
  - Winner receives: `lockup_deposit - P_pay`.
  - All losing bidders receive: 100% of their `lockup_deposit`.

---

## 2. Engine 2: Step-Decay Dutch Auction

### 2.1. Price Decay Formula (Stateless Math)
The price at any given moment `t` is calculated instantaneously without database writes:

```
P(t) = max(floor_price, start_price - step_decrement * floor((t - start_time) / step_interval_seconds))
```

- `start_price`: Starting ceiling price.
- `floor_price`: Minimum reserve floor price.
- `step_decrement`: Price drop per tick interval.
- `step_interval_seconds`: Duration of each step in seconds.
- `start_time`: Auction start timestamp.

### 2.2. Current Step & Next Step Calculation
- Current step index: `current_step = floor((t - start_time) / step_interval_seconds)`
- Time remaining until next step:
  `next_step_in_seconds = step_interval_seconds - ((t - start_time) % step_interval_seconds)`

### 2.3. Buy Now & Concurrency Handling
- When `POST /api/auctions/{id}/buy-dutch` is invoked:
  1. Validate that the auction is `ACTIVE` and `now < end_time`.
  2. Compute `P(now)`.
  3. Atomically update auction status to `SOLD`, set `winner_id = buyer_id`, `final_price = P(now)`, and increment `version`.
  4. If atomic update fails due to version mismatch, immediately throw `AuctionAlreadySoldException` mapped to HTTP `409 Conflict`.
