# REST API Specification

Kronyka exposes a RESTful JSON API. All authenticated endpoints require the `Authorization` header with a Bearer JWT:  
`Authorization: Bearer <jwt-token>`

Interactive API documentation is also generated automatically via OpenAPI / Swagger at:  
`/swagger-ui/index.html` (or `/swagger-ui.html`)

---

## Endpoint Summary (11 Endpoints)

| Method | Endpoint | Auth | Purpose |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Register new user account |
| `POST` | `/api/auth/login` | Public | Authenticate and obtain JWT token |
| `GET` | `/api/auth/me` | Protected | Get profile of the current authenticated user |
| `POST` | `/api/auctions` | Protected | Create a new Vickrey or Dutch auction |
| `GET` | `/api/auctions` | Public | Search & list auctions with filtering & pagination |
| `GET` | `/api/auctions/{id}` | Public | Get detailed auction information |
| `DELETE` | `/api/auctions/{id}` | Protected | Cancel an auction (creator only, prior to bids/sales) |
| `POST` | `/api/auctions/{id}/buy-dutch` | Protected | Buy Now item at current Dutch step price |
| `POST` | `/api/auctions/{id}/commit` | Protected | Submit blind hash commitment and lockup deposit |
| `POST` | `/api/auctions/{id}/reveal` | Protected | Reveal actual bid & secret salt for verification |
| `POST` | `/api/auctions/{id}/settle` | Protected | Settle Vickrey auction (Second-Price Rule) |

---

## 1. Authentication Endpoints

### 1.1. User Registration
`POST /api/auth/register`
- **Request Body**:
  ```json
  {
    "username": "alice",
    "password": "Password123@",
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

### 1.2. User Login
`POST /api/auth/login`
- **Request Body**:
  ```json
  {
    "username": "alice",
    "password": "Password123@"
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

### 1.3. Get Profile
`GET /api/auth/me`
- **Header**: `Authorization: Bearer <token>`
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

## 2. Auction Management Endpoints

### 2.1. Create Auction
`POST /api/auctions`
- **Header**: `Authorization: Bearer <token>`
- **Payload for Vickrey**:
  ```json
  {
    "title": "Crypto Domain: rare.eth",
    "type": "VICKREY",
    "reserve_price": 500000,
    "commit_duration_seconds": 3600,
    "reveal_duration_seconds": 1800
  }
  ```
- **Payload for Dutch**:
  ```json
  {
    "title": "Vintage Mechanical Watch",
    "type": "DUTCH",
    "start_price": 5000000,
    "floor_price": 1000000,
    "step_decrement": 100000,
    "step_interval_seconds": 15,
    "duration_seconds": 1800
  }
  ```

### 2.2. Query Auctions
`GET /api/auctions?type=DUTCH&status=ACTIVE&page=0&size=20`

### 2.3. Get Auction Details
`GET /api/auctions/{id}`
- For Vickrey auctions during active phases (`COMMIT` or `REVEAL`), sensitive bid values and blind hashes are withheld; only `total_commitments` count is returned.

### 2.4. Cancel Auction
`DELETE /api/auctions/{id}`
- Permitted only for the creator seller, and only before any commitments (Vickrey) or purchases (Dutch) occur.

---

## 3. Dutch Auction Engine

### 3.1. Buy Now
`POST /api/auctions/{id}/buy-dutch`
- **Success `200 OK`**: Transitions auction to `SOLD`, sets winner and final purchase price.
- **Conflict `409 Conflict`**: Returned when another buyer won the item first during concurrent submissions.

---

## 4. Vickrey Sealed-Bid Engine

### 4.1. Submit Blind Commitment (Phase 1)
`POST /api/auctions/{id}/commit`
- **Body**: `{ "blind_hash": "<sha256-hex>", "lockup_deposit": 5000000 }`
- **Rule**: `lockup_deposit` must be greater than or equal to the actual concealed bid.

### 4.2. Reveal Bid (Phase 2)
`POST /api/auctions/{id}/reveal`
- **Body**: `{ "actual_bid": 3200000, "secret_salt": "my_secret_salt_xyz" }`
- **Success `200 OK`**: Hash is verified against `blind_hash` and bid confirmed.
- **Failure `400 Bad Request`**: Error code `REVEAL_MISMATCH`. User may retry before the reveal window expires.

### 4.3. Settle Auction (Phase 3)
`POST /api/auctions/{id}/settle`
- **Rule**: Triggered after `reveal_end_time`.
- Winner pays the 2nd highest price (or `reserve_price` if only 1 valid revealed bid exists).
- Deposits are refunded (winner gets `deposit - price`, losing bidders receive 100% deposit back).
