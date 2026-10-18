package example.kronyka.domain.service

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

        if (auction.sellerId != userId) {
            throw UnauthorizedAuctionActionException()
        }

        if (auction.status == AuctionStatus.SETTLED || auction.status == AuctionStatus.CANCELLED) {
            throw InvalidAuctionStateException("Auction canceled or settled")
        }

        if (auction.type == AuctionType.VICKREY) {
            vickreyAuctionService.validateCanCancel(auction.id)
        }

        val cancelledAuction = when (auction) {
            is VickreyAuction -> auction.copy(status = AuctionStatus.CANCELLED)
            else -> TODO("Dutch Auction")
        }

        auctionRepository.save(cancelledAuction)
        return cancelledAuction
    }
}