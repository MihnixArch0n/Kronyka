package example.kronyka.domain.service

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionCancellationException
import example.kronyka.domain.model.AuctionNotFoundException
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.InvalidAuctionStateException
import example.kronyka.domain.model.InvalidAuctionTimeException
import example.kronyka.domain.model.InvalidReservePriceException
import example.kronyka.domain.model.UnauthorizedAuctionActionException
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.repository.AuctionRepository
import example.kronyka.domain.repository.VickreyBidRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AuctionService(
    private val auctionRepository: AuctionRepository,
    private val bidRepository: VickreyBidRepository
) {
    fun getAuction(auctionId: Uuid): Auction =
        auctionRepository.findById(auctionId) ?: throw AuctionNotFoundException()

    fun getAuctionCommitmentCount(auctionId: Uuid): Long =
        bidRepository.countCommitmentsByAuctionId(auctionId)

    fun cancelAuction(auctionId: Uuid, userId: Uuid): Auction {
        val auction = auctionRepository.findById(auctionId)
            ?: throw AuctionNotFoundException()

        if (auction.sellerId != userId) throw UnauthorizedAuctionActionException()

        if (auction.status == AuctionStatus.SETTLED
            || auction.status == AuctionStatus.CANCELLED)
            throw InvalidAuctionStateException("Auction canceled or settled")

        val cancelledAuction = when (auction) {
            is VickreyAuction -> {
                if (bidRepository.countCommitmentsByAuctionId(auction.id) > 0) {
                    throw AuctionCancellationException()
                }
                auction.copy(status = AuctionStatus.CANCELLED)
            }
            else -> TODO("Dutch auction")
        }
        auctionRepository.save(cancelledAuction)
        return cancelledAuction
    }
}