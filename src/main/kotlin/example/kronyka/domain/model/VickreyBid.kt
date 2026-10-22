package example.kronyka.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class VickreyBid(
    val id: Uuid,
    val auctionId: Uuid,
    val bidderId: Uuid,
    val blindHash: String,
    val lockupDeposit: Long,
    val actualBid: Long? = null,
    val isRevealed: Boolean = false,
    val createdAt: Instant,
    val revealedAt: Instant? = null
)
