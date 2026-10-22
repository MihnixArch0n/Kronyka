package example.kronyka.domain.repository

import example.kronyka.domain.model.VickreyBid
import kotlin.uuid.Uuid

interface VickreyBidRepository {
    fun findByAuctionIdAndBidderId(auctionId: Uuid, bidderId: Uuid): VickreyBid?
    fun upsert(bid: VickreyBid)
    fun findAllRevealedBidsByAuctionId(auctionId: Uuid): List<VickreyBid>
    fun countCommitmentsByAuctionId(auctionId: Uuid): Long
}