package example.kronyka.domain.repository

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.VickreyAuction
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AuctionRepository {
    fun findById(id: Uuid): Auction?
    fun save(auction: Auction)
    fun findExpiredUnsettledVickreyAuctions(beforeTime: Instant, limit: Int = 50): List<VickreyAuction>
}
