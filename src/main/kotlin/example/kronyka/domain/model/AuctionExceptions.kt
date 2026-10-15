package example.kronyka.domain.model

class AuctionCancellationException : RuntimeException()

class AuctionNotFoundException : RuntimeException()

class BidNotFoundException : RuntimeException()

class InvalidAuctionStateException(message: String) : RuntimeException(message)

class InvalidAuctionTypeException : RuntimeException()

class InvalidDepositException : RuntimeException()

class RevealMismatchException : RuntimeException()

class UnauthorizedAuctionActionException : RuntimeException()

class InvalidReservePriceException : RuntimeException()

class InvalidAuctionTimeException : RuntimeException()