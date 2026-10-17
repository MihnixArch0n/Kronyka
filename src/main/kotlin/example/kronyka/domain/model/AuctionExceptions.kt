package example.kronyka.domain.model

sealed class AuctionException(message: String? = null) : RuntimeException(message)

class AuctionCancellationException : AuctionException()

class AuctionNotFoundException : AuctionException()

class BidNotFoundException : AuctionException()

class InvalidAuctionStateException(message: String) : AuctionException(message)

class InvalidAuctionTypeException : AuctionException()

class InvalidDepositException : AuctionException()

class RevealMismatchException : AuctionException()

class UnauthorizedAuctionActionException : AuctionException()

class InvalidReservePriceException : AuctionException()

class InvalidAuctionTimeException : AuctionException()