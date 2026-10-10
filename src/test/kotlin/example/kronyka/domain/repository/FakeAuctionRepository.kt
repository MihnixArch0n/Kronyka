package example.kronyka.domain.repository

import example.kronyka.domain.model.Auction
import kotlin.uuid.Uuid

class FakeAuctionRepository : AuctionRepository {
    private val auctions = mutableMapOf<Uuid, Auction>()

    override fun findById(id: Uuid): Auction? = auctions[id]

    override fun save(auction: Auction) {
        auctions[auction.id] = auction
    }

    fun clear() {
        auctions.clear()
    }
}
