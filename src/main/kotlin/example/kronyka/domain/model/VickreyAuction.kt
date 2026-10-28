package example.kronyka.domain.model

import kotlin.time.Clock
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
) : Auction {

    private fun validateNotTerminated() {
        if (status == AuctionStatus.CANCELLED || status == AuctionStatus.SETTLED) {
            throw InvalidAuctionStateException("Auction is ${status.name.lowercase()}")
        }
    }

    fun ensureCommitPhase(now: Instant) {
        validateNotTerminated()
        if (now !in startTime..<commitEndTime) {
            throw InvalidAuctionStateException("Not in commit phase")
        }
    }

    fun ensureRevealPhase(now: Instant) {
        validateNotTerminated()
        if (now !in commitEndTime..<revealEndTime) {
            throw InvalidAuctionStateException("Not on reveal phase")
        }
    }

    fun ensureSettlePhase(now: Instant) {
        if (status == AuctionStatus.CANCELLED) throw InvalidAuctionStateException("Auction is cancelled")
        if (status == AuctionStatus.SETTLED) throw InvalidAuctionStateException("Auction is already settled")
        if (now < revealEndTime) throw InvalidAuctionStateException("Not on settle phase")
    }

    fun resolveStatus(now: Instant = Clock.System.now()): AuctionStatus = when {
        status == AuctionStatus.CANCELLED -> AuctionStatus.CANCELLED
        status == AuctionStatus.SETTLED -> AuctionStatus.SETTLED
        now < commitEndTime -> AuctionStatus.COMMIT
        now < revealEndTime -> AuctionStatus.REVEAL
        else -> AuctionStatus.EXPIRED
    }
}
