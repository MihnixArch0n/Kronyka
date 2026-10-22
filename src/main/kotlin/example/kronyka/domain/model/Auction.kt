package example.kronyka.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class AuctionStatus {
    COMMIT, REVEAL, ACTIVE, SOLD, SETTLED, CANCELLED
}

enum class AuctionType {
    DUTCH, VICKREY
}

interface Auction {
    val id: Uuid
    val sellerId: Uuid
    val title: String
    val type: AuctionType
    val status: AuctionStatus
    val startTime: Instant
    val endTime: Instant
    val winnerId: Uuid?
    val finalPrice: Long?
    val version: Long
    val createdAt: Instant
}