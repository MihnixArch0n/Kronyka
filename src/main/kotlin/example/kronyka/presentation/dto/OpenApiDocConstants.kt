package example.kronyka.presentation.dto

/**
 * Raw JSON constants representing standardized error response payloads for OpenAPI / Scalar documentation.
 *
 * All error examples adhere to Kronyka's ApiResponse<Unit> error envelope:
 * { "code": <Int>, "message": "<String>" }
 */
object OpenApiDocConstants {

    // --- Authentication Errors ---
    const val AUTH_400_VALIDATION_ERROR = """{
  "code": 1005,
  "message": "username: must not be blank; password: size must be between 6 and 2147483647"
}"""

    const val AUTH_401_INVALID_CREDENTIALS = """{
  "code": 1002,
  "message": "Invalid username or password"
}"""

    const val AUTH_401_UNAUTHENTICATED = """{
  "code": 1004,
  "message": "Unauthenticated or invalid token"
}"""

    const val AUTH_404_USER_NOT_FOUND = """{
  "code": 1003,
  "message": "User not found"
}"""

    const val AUTH_409_USER_EXISTS = """{
  "code": 1001,
  "message": "User already exists"
}"""

    // --- Auction Query & Creation Errors ---
    const val AUCTION_400_VALIDATION_ERROR = """{
  "code": 1005,
  "message": "title: must not be blank; reserve_price: must be greater than or equal to 0"
}"""

    const val AUCTION_400_INVALID_TIME = """{
  "code": 2011,
  "message": "Invalid auction timeline configuration: commitEndTime must be before revealEndTime"
}"""

    const val AUCTION_400_INVALID_RESERVE_PRICE = """{
  "code": 2010,
  "message": "Reserve price must be non-negative"
}"""

    const val AUCTION_400_QUERY_BAD_REQUEST = """{
  "code": 1007,
  "message": "Size must be between 1 and 100"
}"""

    const val AUCTION_400_TYPE_MISMATCH = """{
  "code": 1007,
  "message": "Invalid value for parameter 'id'"
}"""

    const val AUCTION_401_UNAUTHENTICATED = """{
  "code": 1004,
  "message": "Unauthenticated or invalid token"
}"""

    const val AUCTION_403_UNAUTHORIZED_ACTION = """{
  "code": 2009,
  "message": "You are not authorized to perform this action on the auction"
}"""

    const val AUCTION_404_NOT_FOUND = """{
  "code": 2001,
  "message": "Auction not found"
}"""

    const val AUCTION_400_INVALID_STATE_CANCEL = """{
  "code": 2006,
  "message": "Auction canceled or settled"
}"""

    const val AUCTION_409_CANCELLATION_FAILED = """{
  "code": 2004,
  "message": "Auction cannot be cancelled once bids are committed or auction is closed"
}"""

    // --- Vickrey Commitment Errors ---
    const val COMMIT_400_HASH_FORMAT = """{
  "code": 1005,
  "message": "blind_hash: Blind hash must be a 64-character hexadecimal SHA-256 string"
}"""

    const val COMMIT_400_INVALID_DEPOSIT = """{
  "code": 2008,
  "message": "Lockup deposit must be greater than or equal to reserve price"
}"""

    const val COMMIT_400_INVALID_STATE = """{
  "code": 2006,
  "message": "Auction is not in the valid phase for this action"
}"""

    const val COMMIT_400_INVALID_TYPE = """{
  "code": 2007,
  "message": "Invalid auction type for this operation"
}"""

    // --- Vickrey Reveal Errors ---
    const val REVEAL_400_MISMATCH = """{
  "code": 2003,
  "message": "Blind commitment hash mismatch"
}"""

    const val REVEAL_400_INVALID_STATE = """{
  "code": 2006,
  "message": "Auction is not in the valid phase for this action"
}"""

    const val REVEAL_400_VALIDATION_ERROR = """{
  "code": 1005,
  "message": "actual_bid: must be greater than 0"
}"""

    const val REVEAL_404_BID_NOT_FOUND = """{
  "code": 2005,
  "message": "Bid not found for this auction"
}"""

    // --- Dutch Auction Errors ---
    const val DUTCH_400_VALIDATION_ERROR = """{
  "code": 1005,
  "message": "title: must not be blank; start_price: must be greater than 0; step_decrement: must be greater than 0"
}"""

    const val DUTCH_400_INVALID_FLOOR_PRICE = """{
  "code": 1007,
  "message": "Invalid floor price"
}"""

    const val DUTCH_400_INVALID_TIMELINE = """{
  "code": 1007,
  "message": "End time must be after start time"
}"""

    const val DUTCH_400_NOT_DUTCH_TYPE = """{
  "code": 2006,
  "message": "Auction is not a Dutch auction"
}"""

    const val DUTCH_400_NOT_ACTIVE = """{
  "code": 2006,
  "message": "Dutch auction is not active"
}"""

    const val DUTCH_400_OUTSIDE_WINDOW = """{
  "code": 2006,
  "message": "Dutch auction is outside its active window"
}"""

    const val DUTCH_400_SELF_PURCHASE = """{
  "code": 2006,
  "message": "Seller cannot buy their own auction"
}"""

    const val DUTCH_409_PURCHASE_CONFLICT = """{
  "code": 2012,
  "message": "Dutch auction has already been purchased or modified"
}"""

    const val DUTCH_409_ALREADY_SOLD = """{
  "code": 2012,
  "message": "Dutch auction has already been sold"
}"""
}
