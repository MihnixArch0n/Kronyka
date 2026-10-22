package example.kronyka.domain.model

import kotlin.uuid.Uuid

data class SettlementResult(
    val winnerId: Uuid? = null,
    val auctionId: Uuid,
    val finalPrice: Long? = null,
    val secondBidderId: Uuid? = null,
)
