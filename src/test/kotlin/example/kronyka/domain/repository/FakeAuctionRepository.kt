package example.kronyka.domain.repository

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionPage
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.VickreyAuction
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeAuctionRepository : AuctionRepository {
    private val auctions = mutableMapOf<Uuid, Auction>()

    override fun findById(id: Uuid): Auction? = auctions[id]

    override fun findAll(type: AuctionType?, status: AuctionStatus?, offset: Long, limit: Int): AuctionPage {
        val filtered = auctions.values
            .filter { (type == null || it.type == type) && (status == null || it.status == status) }
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
}

