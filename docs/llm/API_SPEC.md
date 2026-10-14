# REST API Specification (LLM Context)

Base URL prefix: `/api`  
Auth Header: `Authorization: Bearer <token>`

---

## 1. Authentication Endpoints

### 1.1. Register User
- **Method**: `POST`
- **Path**: `/api/auth/register`
- **Auth**: Public
- **Request Body**:
```json
{
  "username": "alice",
  "password": "SecurePassword123@",
  "full_name": "Alice Wonderland"
}
```
- **Response `201 Created`**:
```json
{
  "user_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "username": "alice",
  "full_name": "Alice Wonderland",
  "message": "User registered successfully"
}
```
- **Error `409 Conflict`**: Username already exists.

### 1.2. Login
- **Method**: `POST`
- **Path**: `/api/auth/login`
- **Auth**: Public
- **Request Body**:
```json
{
  "username": "alice",
  "password": "SecurePassword123@"
}
```
- **Response `200 OK`**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsIn...",
  "token_type": "Bearer",
  "expires_in": 86400,
  "user": {
    "user_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "username": "alice"
  }
}
```
- **Error `401 Unauthorized`**: Invalid credentials.

### 1.3. Get Current User Profile
- **Method**: `GET`
- **Path**: `/api/auth/me`
- **Auth**: Bearer Token
- **Response `200 OK`**:
```json
{
  "user_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "username": "alice",
  "full_name": "Alice Wonderland",
  "created_at": "2026-10-01T08:00:00Z"
}
```

---

## 2. Common Auction Management Endpoints

### 2.1. Create Auction
- **Method**: `POST`
- **Path**: `/api/auctions`
- **Auth**: Bearer Token (Seller)

#### Payload A: Vickrey Auction
```json
{
  "title": "Rare Digital Asset: crypto.wallet",
  "type": "VICKREY",
  "reserve_price": 500000,
  "commit_duration_seconds": 3600,
  "reveal_duration_seconds": 1800
}
```

#### Payload B: Dutch Auction
```json
{
  "title": "Limited Card #001",
  "type": "DUTCH",
  "start_price": 2000000,
  "floor_price": 500000,
  "step_decrement": 50000,
  "step_interval_seconds": 15,
  "duration_seconds": 1800
}
```

- **Response `201 Created`**:
```json
{
  "auction_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "title": "Rare Digital Asset: crypto.wallet",
  "type": "VICKREY",
  "status": "COMMIT",
  "seller_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "start_time": "2026-10-01T08:00:00Z",
  "commit_end_time": "2026-10-01T09:00:00Z",
  "reveal_end_time": "2026-10-01T09:30:00Z",
  "end_time": "2026-10-01T09:30:00Z"
}
```

### 2.2. Query Auctions
- **Method**: `GET`
- **Path**: `/api/auctions?type=DUTCH&status=ACTIVE&page=0&size=20`
- **Auth**: Public
- **Response `200 OK`**:
```json
{
  "total": 1,
  "items": [
    {
      "auction_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "title": "Limited Card #001",
      "type": "DUTCH",
      "status": "ACTIVE",
      "current_price": 1750000,
      "current_step": 5,
      "next_step_in_seconds": 8,
      "floor_price": 500000
    }
  ]
}
```

### 2.3. Get Auction Detail
- **Method**: `GET`
- **Path**: `/api/auctions/{id}`
- **Auth**: Public
- **Rules**: For active Vickrey (`COMMIT` / `REVEAL`), never expose bid amounts or hashes. Only return `total_commitments`.
- **Response `200 OK` (Vickrey Example)**:
```json
{
  "auction_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "title": "Rare Digital Asset: crypto.wallet",
  "type": "VICKREY",
  "status": "COMMIT",
  "total_commitments": 14,
  "commit_end_time": "2026-10-01T09:00:00Z"
}
```

### 2.4. Cancel Auction
- **Method**: `DELETE`
- **Path**: `/api/auctions/{id}`
- **Auth**: Bearer Token (Must be creator seller)
- **Constraint**: Cannot cancel if any bid was committed (Vickrey) or if already purchased (Dutch).
- **Response `200 OK`**:
```json
{
  "message": "Auction cancelled successfully",
  "auction_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

---

## 3. Dutch Auction Engine Endpoints

### 3.1. Instant Buy Now
- **Method**: `POST`
- **Path**: `/api/auctions/{id}/buy-dutch`
- **Auth**: Bearer Token (Buyer)
- **Response `200 OK` (Winner)**:
```json
{
  "auction_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SOLD",
  "purchased_price": 1750000,
  "buyer_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "purchased_at": "2026-10-01T08:05:22.415Z",
  "message": "Congratulations! You won the Dutch auction."
}
```
- **Response `409 Conflict` (Latecomer / Race Lost)**:
```json
{
  "timestamp": "2026-10-01T08:05:22.430Z",
  "status": 409,
  "error": "AUCTION_ALREADY_SOLD",
  "message": "Item was already purchased by another bidder.",
  "path": "/api/auctions/3fa85f64-5717-4562-b3fc-2c963f66afa6/buy-dutch"
}
```

---

## 4. Vickrey Sealed-Bid Engine Endpoints

### 4.1. Submit Blind Commitment (Phase 1)
- **Method**: `POST`
- **Path**: `/api/auctions/{id}/commit`
- **Auth**: Bearer Token (Bidder)
- **Request Body**:
```json
{
  "blind_hash": "e7a8b92c63d4f107389a456bcdef123456789abcdef0123456789abcdef01234",
  "lockup_deposit": 5000000
}
```
- **Response `201 Created`**:
```json
{
  "bid_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "auction_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "COMMITTED",
  "locked_deposit": 5000000,
  "message": "Blind commitment recorded."
}
```

### 4.2. Reveal Bid (Phase 2)
- **Method**: `POST`
- **Path**: `/api/auctions/{id}/reveal`
- **Auth**: Bearer Token (Bidder)
- **Request Body**:
```json
{
  "actual_bid": 3200000,
  "secret_salt": "alice_secret_salt_xyz999"
}
```
- **Response `200 OK` (Verified)**:
```json
{
  "bid_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "REVEALED_VALID",
  "actual_bid": 3200000,
  "message": "Bid revealed successfully."
}
```
- **Error `400 Bad Request` (Mismatch - Can Retry)**:
```json
{
  "timestamp": "2026-10-01T09:10:00Z",
  "status": 400,
  "error": "REVEAL_MISMATCH",
  "message": "Actual bid or secret salt does not match committed blind hash. Please verify and retry before reveal phase ends.",
  "path": "/api/auctions/3fa85f64-5717-4562-b3fc-2c963f66afa6/reveal"
}
```

### 4.3. Settle Auction (Phase 3)
- **Method**: `POST`
- **Path**: `/api/auctions/{id}/settle`
- **Auth**: Bearer Token (Seller or Worker)
- **Response `200 OK`**:
```json
{
  "auction_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SETTLED",
  "winner_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "highest_bid": 3200000,
  "final_settled_price": 2800000,
  "second_bidder_id": "3fa85f64-5717-4562-b3fc-2c963f66afa7",
  "refund_for_winner": 2200000,
  "message": "Winner pays second-highest price (2,800,000 VND) and receives 2,200,000 VND deposit refund."
}
```
