package example.kronyka.domain.repository

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.VickreyAuction
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeAuctionRepository : AuctionRepository {
    private val auctions = mutableMapOf<Uuid, Auction>()

    override fun findById(id: Uuid): Auction? = auctions[id]

    override fun save(auction: Auction) {
        auctions[auction.id] = auction
    }

    override fun findExpiredUnsettledVickreyAuctions(beforeTime: Instant, limit: Int): List<VickreyAuction> =
        auctions.values
            .filterIsInstance<VickreyAuction>()
            .filter { it.status != AuctionStatus.SETTLED && it.status != AuctionStatus.CANCELLED && it.revealEndTime <= beforeTime }
            .sortedBy { it.revealEndTime }
            .take(limit)

    fun clear() {
        auctions.clear()
    }
}

