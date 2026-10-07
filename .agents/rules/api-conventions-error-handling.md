---
trigger: model_decision
description: "REST API contract conventions, validation guidelines, and error handling standards for Kronyka."
---

# API Conventions & Error Handling Guidelines

## 1. REST Endpoint Specifications (11 Endpoints)

| Method | Endpoint | Auth Required | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | No | User registration |
| `POST` | `/api/auth/login` | No | User login (returns JWT token) |
| `GET` | `/api/auth/me` | Yes | Get currently authenticated user details |
| `POST` | `/api/auctions` | Yes | Create Vickrey or Dutch auction |
| `GET` | `/api/auctions` | No | List auctions (supports filtering & pagination) |
| `GET` | `/api/auctions/{id}` | No | Get auction details (hides sensitive bids) |
| `DELETE` | `/api/auctions/{id}` | Yes | Cancel auction (seller only, no active bids/sales) |
| `POST` | `/api/auctions/{id}/buy-dutch` | Yes | Buy Now at current Dutch step price |
| `POST` | `/api/auctions/{id}/commit` | Yes | Vickrey Phase 1: Submit blind hash & deposit |
| `POST` | `/api/auctions/{id}/reveal` | Yes | Vickrey Phase 2: Reveal bid & salt |
| `POST` | `/api/auctions/{id}/settle` | Yes | Vickrey Phase 3: Settle auction by second price |

## 2. Request & Response DTOs
- Separate Request and Response DTOs from domain models.
- Apply Jakarta Validation annotations on Request DTOs:
  - `@field:NotBlank`, `@field:Size(min = 6)`
  - `@field:Positive`, `@field:PositiveOrZero`
  - `@field:NotNull`
- Enclose all responses in clean JSON contracts without circular references.

## 3. Standardized Error Response Structure
All API errors must return the following JSON structure via `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-10-08T03:30:00Z",
  "status": 409,
  "error": "AUCTION_ALREADY_SOLD",
  "message": "Item was already purchased by another bidder at 2026-10-08T03:29:55Z.",
  "path": "/api/auctions/3fa85f64-5717-4562-b3fc-2c963f66afa6/buy-dutch"
}
```

## 4. Exception Mapping Rules
- `MethodArgumentNotValidException` -> `400 Bad Request` with field validation errors in message.
- `IllegalArgumentException` / `DomainValidationException` -> `400 Bad Request`.
- `RevealMismatchException` -> `400 Bad Request` (error code: `REVEAL_MISMATCH`).
- `AuthenticationException` / `BadCredentialsException` -> `401 Unauthorized`.
- `AccessDeniedException` -> `403 Forbidden`.
- `EntityNotFoundException` / `AuctionNotFoundException` -> `404 Not Found`.
- `ConcurrencyConflictException` / `AuctionAlreadySoldException` -> `409 Conflict`.
- `UserAlreadyExistsException` -> `409 Conflict`.
- Unhandled `Exception` -> `500 Internal Server Error`.
