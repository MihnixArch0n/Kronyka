package example.kronyka.presentation.dto

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val code: Int,
    val message: String,
    val httpStatus: HttpStatus
) {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_ALREADY_EXISTS(1001, "User already exists", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(1002, "Invalid username or password", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(1003, "User not found", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1004, "Unauthenticated or invalid token", HttpStatus.UNAUTHORIZED),
    VALIDATION_FAILED(1005, "Validation failed", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON(1006, "Malformed JSON request body", HttpStatus.BAD_REQUEST),
    BAD_REQUEST(1007, "Invalid request argument", HttpStatus.BAD_REQUEST),
    DATA_INTEGRITY_VIOLATION(1008, "Data integrity violation", HttpStatus.CONFLICT),
    AUCTION_NOT_FOUND(2001, "Auction not found", HttpStatus.NOT_FOUND),
    AUCTION_ALREADY_SOLD(2002, "Auction already sold", HttpStatus.CONFLICT),
    REVEAL_MISMATCH(2003, "Blind commitment hash mismatch", HttpStatus.BAD_REQUEST),
    AUCTION_CANCELLATION_FAILED(2004, "Auction cannot be cancelled once bids are committed or auction is closed", HttpStatus.CONFLICT),
    BID_NOT_FOUND(2005, "Bid not found for this auction", HttpStatus.NOT_FOUND),
    INVALID_AUCTION_STATE(2006, "Auction is not in the valid phase for this action", HttpStatus.BAD_REQUEST),
    INVALID_AUCTION_TYPE(2007, "Invalid auction type for this operation", HttpStatus.BAD_REQUEST),
    INVALID_DEPOSIT(2008, "Lockup deposit must be greater than or equal to reserve price", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ACTION(2009, "You are not authorized to perform this action on the auction", HttpStatus.FORBIDDEN),
    INVALID_RESERVE_PRICE(
        2010,
        "Reserve price must be non-negative",
        HttpStatus.BAD_REQUEST
    ),

    INVALID_AUCTION_TIME(
        2011,
        "Invalid auction timeline configuration",
        HttpStatus.BAD_REQUEST
    ),

    DUTCH_PURCHASE_CONFLICT(
        2012,
        "Dutch auction has already been purchased or modified",
        HttpStatus.CONFLICT
    )
}

