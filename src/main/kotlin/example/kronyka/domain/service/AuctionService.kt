package example.kronyka.domain.service

import example.kronyka.domain.model.DutchAuction
import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionNotFoundException
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.InvalidAuctionStateException
import example.kronyka.domain.model.UnauthorizedAuctionActionException
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.repository.AuctionRepository
import kotlin.uuid.Uuid

class AuctionService(
    private val auctionRepository: AuctionRepository,
    private val vickreyAuctionService: VickreyAuctionService
) {
    fun getAuction(auctionId: Uuid): Auction =
        auctionRepository.findById(auctionId) ?: throw AuctionNotFoundException()

    fun cancelAuction(auctionId: Uuid, userId: Uuid): Auction {
        val auction = getAuction(auctionId)

        // Only the seller can cancel their auction
        if (auction.sellerId != userId) {
            throw UnauthorizedAuctionActionException()
        }

        // Cannot cancel an already finished or cancelled auction
        if (
            auction.status == AuctionStatus.SETTLED ||
            auction.status == AuctionStatus.CANCELLED ||
            auction.status == AuctionStatus.SOLD
        ) {
            throw InvalidAuctionStateException(
                "Auction canceled or settled"
            )
        }

        // Keep existing Vickrey cancellation rules
        if (auction.type == AuctionType.VICKREY) {
            vickreyAuctionService.validateCanCancel(auction.id)
        }

        val cancelledAuction = when (auction) {
            is VickreyAuction -> auction.copy(
                status = AuctionStatus.CANCELLED
            )

            is DutchAuction -> {
                if (auction.winnerId != null) {
                    throw InvalidAuctionStateException(
                        "Cannot cancel a Dutch auction that has been purchased"
                    )
                }

                auction.copy(
                    status = AuctionStatus.CANCELLED
                )
            }

            else -> throw InvalidAuctionStateException(
                "Unsupported auction type"
            )
        }

        if (auction is DutchAuction) {
            val success = auctionRepository.tryCancelDutchAuction(
                auctionId = auction.id,
                sellerId = userId,
                expectedVersion = auction.version
            )

            if (!success) {
                throw InvalidAuctionStateException(
                    "Dutch auction has already been purchased or modified"
                )
            }

            return auction.copy(
                status = AuctionStatus.CANCELLED,
                version = auction.version + 1
            )
        }

        auctionRepository.save(cancelledAuction)
        return cancelledAuction
    }
}
