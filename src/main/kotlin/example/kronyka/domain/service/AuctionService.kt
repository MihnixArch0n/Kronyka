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
    @OptIn(ExperimentalUuidApi::class)
    fun createVickreyAuction(
        sellerId: Uuid,
        title: String,
        startTime: Instant,
        reservePrice: Long,
        commitEndTime: Instant,
        revealEndTime: Instant,
    ): VickreyAuction {
        if (reservePrice < 0) throw InvalidReservePriceException()

        val now = Clock.System.now()

        if (now > startTime
            || startTime > commitEndTime
            || startTime > revealEndTime
            || commitEndTime > revealEndTime)
            throw InvalidAuctionTimeException()

        val auction = VickreyAuction(
            id = Uuid.generateV7(),
            sellerId = sellerId,
            title = title,
            type = AuctionType.VICKREY,
            status = AuctionStatus.COMMIT,
            startTime = startTime,
            endTime = revealEndTime,
            version = 0L,
            createdAt = now,
            reservePrice = reservePrice,
            commitEndTime = commitEndTime,
            revealEndTime = revealEndTime,
        )
        auctionRepository.save(auction)
        return auction
    }

    fun getAuction(auctionId: Uuid): Auction =
        auctionRepository.findById(auctionId) ?: throw AuctionNotFoundException()

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