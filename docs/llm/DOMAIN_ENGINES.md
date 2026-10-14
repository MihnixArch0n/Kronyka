# Dual-Engine Auction Logic & Mathematical Models (LLM Context)

## 1. Engine 1: Vickrey Sealed-Bid Blind Auction

### 1.1. Three-Phase State Machine
```
[COMMIT] ---> [REVEAL] ---> [SETTLED]
```

1. **Commit Phase (`COMMIT`)**:
   - Time window: `start_time` to `commit_end_time`.
   - Client generates:
     `blind_hash = SHA-256(actual_bid + "_" + secret_salt)`
   - Client submits: `{ blind_hash, lockup_deposit }` where `lockup_deposit >= actual_bid`.
   - Neither server nor other bidders know `actual_bid`.
   - Repeated submissions by the same bidder safely update their commitment.

2. **Reveal Phase (`REVEAL`)**:
   - Time window: `commit_end_time` to `reveal_end_time`.
   - Client submits: `{ actual_bid, secret_salt }`.
   - Server verification checks:
     1. `SHA-256(actual_bid + "_" + secret_salt) == committed_blind_hash`
     2. `actual_bid <= committed_lockup_deposit`
   - If verification fails: returns `400 Bad Request` (`REVEAL_MISMATCH`). Bidder can retry until `reveal_end_time`.
   - If verified: sets `is_revealed = true`, saves `actual_bid` and `revealed_at`.

3. **Settle Phase (`SETTLED`)**:
   - Triggered after `reveal_end_time` by seller or worker.
   - Filters bids where `is_revealed == true`, sorted descending by `actual_bid`.
   - Highest bidder (1st) wins.
   - Final price paid `P_pay` = 2nd highest bid.
     - If only 1 valid revealed bid exists: `P_pay = reserve_price`.
     - If 0 valid revealed bids exist: auction concludes unsold (`winner_id = null`, `final_price = null`).
   - Deposit refund calculations:
     - Winner receives: `lockup_deposit - P_pay`
     - Losers receive: 100% of their `lockup_deposit`

---

## 2. Engine 2: Step-Decay Dutch Auction

### 2.1. Price Decay Formula (Stateless Math)
The price at any point in time `t` is computed directly in memory:

```
P(t) = max(floor_price, start_price - step_decrement * floor((t - start_time) / step_interval_seconds))
```

- `start_price`: Starting ceiling price
- `floor_price`: Minimum reserve price
- `step_decrement`: Price decrease per step
- `step_interval_seconds`: Duration of each step interval in seconds
- `start_time`: Auction start timestamp

### 2.2. Current Step & Next Step Calculation
- Step index: `current_step = floor((t - start_time) / step_interval_seconds)`
- Time until next price drop:
  `next_step_in_seconds = step_interval_seconds - ((t - start_time) % step_interval_seconds)`

### 2.3. Buy Now Concurrency Handling
When `POST /api/auctions/{id}/buy-dutch` is received:
1. Verify auction is `ACTIVE` and `now <= end_time`.
2. Compute `P(now)`.
3. Perform atomic update with version check:
   ```sql
   UPDATE auctions
   SET status = 'SOLD', winner_id = :buyer_id, final_price = :price, version = version + 1
   WHERE id = :auction_id AND status = 'ACTIVE' AND version = :expected_version;
   ```
4. If updated row count is 0: throw `ConcurrencyConflictException` (HTTP `409 Conflict`).
