package example.kronyka.domain.repository

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.DutchAuction
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
    override fun tryCompleteDutchPurchase(
        auctionId: Uuid,
        buyerId: Uuid,
        expectedVersion: Long,
        finalPrice: Long,
        now: Instant
    ): Boolean {
        val auction = findById(auctionId) as? DutchAuction ?: return false

        if (auction.status != AuctionStatus.ACTIVE) return false
        if (auction.winnerId != null) return false
        if (auction.version != expectedVersion) return false
        if (now < auction.startTime || now >= auction.endTime) return false
        if (finalPrice < 0) return false

        save(
            auction.copy(
                status = AuctionStatus.SOLD,
                winnerId = buyerId,
                finalPrice = finalPrice,
                version = auction.version + 1
            )
        )

        return true
    }
    override fun tryCancelDutchAuction(
        auctionId: Uuid,
        sellerId: Uuid,
        expectedVersion: Long
    ): Boolean {
        val auction = findById(auctionId) as? DutchAuction
            ?: return false

        if (
            auction.sellerId != sellerId ||
            auction.status != AuctionStatus.ACTIVE ||
            auction.winnerId != null ||
            auction.version != expectedVersion
        ) {
            return false
        }

        save(
            auction.copy(
                status = AuctionStatus.CANCELLED,
                version = expectedVersion + 1
            )
        )

        return true
    }
}

