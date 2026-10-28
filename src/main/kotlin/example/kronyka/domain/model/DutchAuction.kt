
package example.kronyka.domain.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class DutchAuction(
    override val id: Uuid,
    override val sellerId: Uuid,
    override val title: String,
    override val type: AuctionType = AuctionType.DUTCH,
    override val status: AuctionStatus,
    override val startTime: Instant,
    override val endTime: Instant,
    override val winnerId: Uuid? = null,
    override val finalPrice: Long? = null,
    override val version: Long,
    override val createdAt: Instant,

    val startPrice: Long,
    val floorPrice: Long,
    val stepDecrement: Long,
    val stepIntervalSeconds: Long
) : Auction {

    init {
        require(type == AuctionType.DUTCH)
        require(startPrice >= floorPrice)
        require(floorPrice >= 0)
        require(stepDecrement > 0)
        require(stepIntervalSeconds > 0)
        require(endTime > startTime)
    }

    fun ensureCanBuy(now: Instant, buyerId: Uuid) {
        if (status != AuctionStatus.ACTIVE) {
            throw InvalidAuctionStateException(
                "Dutch auction is not active"
            )
        }

        if (now < startTime || now >= endTime) {
            throw InvalidAuctionStateException(
                "Dutch auction is outside its active window"
            )
        }

        if (buyerId == sellerId) {
            throw InvalidAuctionStateException(
                "Seller cannot buy their own auction"
            )
        }

        if (winnerId != null) {
            throw InvalidAuctionStateException(
                "Auction already has a winner"
            )
        }
    }

    fun resolveStatus(now: Instant = Clock.System.now()): AuctionStatus = when {
        status == AuctionStatus.CANCELLED -> AuctionStatus.CANCELLED
        status == AuctionStatus.SOLD -> AuctionStatus.SOLD
        now >= endTime -> AuctionStatus.EXPIRED
        else -> AuctionStatus.ACTIVE
    }
}
