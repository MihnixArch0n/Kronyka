package example.kronyka.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class VickreyAuction(
    override val id: Uuid,
    override val sellerId: Uuid,
    override val title: String,
    override val type: AuctionType = AuctionType.VICKREY,
    override val status: AuctionStatus,
    override val startTime: Instant,
    override val endTime: Instant,
    override val winnerId: Uuid? = null,
    override val finalPrice: Long? = null,
    override val version: Long,
    override val createdAt: Instant,
    val reservePrice: Long,
    val commitEndTime: Instant,
    val revealEndTime: Instant,
    val secondBidderId: Uuid? = null
) : Auction
