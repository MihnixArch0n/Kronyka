package example.kronyka.domain.repository

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionPage
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.DutchAuction
import example.kronyka.domain.model.VickreyAuction
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeAuctionRepository : AuctionRepository {
    private val auctions = mutableMapOf<Uuid, Auction>()

    override fun findById(id: Uuid): Auction? = auctions[id]

    override fun findAll(type: AuctionType?, status: AuctionStatus?, offset: Long, limit: Int): AuctionPage {
        val now = Clock.System.now()
        val filtered = auctions.values
            .filter { auction ->
                (type == null || auction.type == type) &&
                (status == null || when (auction) {
                    is VickreyAuction -> auction.resolveStatus(now) == status
                    is DutchAuction -> auction.resolveStatus(now) == status
                    else -> auction.status == status
                })
            }
            .sortedWith(compareByDescending<Auction> { it.createdAt }.thenByDescending { it.id })
        val items = if (offset >= filtered.size.toLong()) emptyList()
            else filtered.drop(offset.toInt()).take(limit)
        return AuctionPage(items, filtered.size.toLong())
    }

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

