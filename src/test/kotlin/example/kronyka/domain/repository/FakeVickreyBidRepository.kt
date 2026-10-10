package example.kronyka.domain.repository

import example.kronyka.domain.model.VickreyBid
import kotlin.uuid.Uuid

class FakeVickreyBidRepository : VickreyBidRepository {
    private val bids = mutableMapOf<Pair<Uuid, Uuid>, VickreyBid>()

    override fun findByAuctionIdAndBidderId(auctionId: Uuid, bidderId: Uuid): VickreyBid? {
        return bids[auctionId to bidderId]
    }

    override fun upsert(bid: VickreyBid) {
        bids[bid.auctionId to bid.bidderId] = bid
    }

    override fun findAllRevealedBidsByAuctionId(auctionId: Uuid): List<VickreyBid> {
        return bids.values.filter { it.auctionId == auctionId && it.isRevealed }
    }

    override fun countCommitmentsByAuctionId(auctionId: Uuid): Long {
        return bids.values.count { it.auctionId == auctionId }.toLong()
    }

    fun clear() {
        bids.clear()
    }
}
