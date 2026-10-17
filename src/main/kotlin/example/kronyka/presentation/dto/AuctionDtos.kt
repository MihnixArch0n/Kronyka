package example.kronyka.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class CreateVickreyAuctionRequest(
    @field:NotBlank val title: String? = null,
    @field:NotNull
    @field:PositiveOrZero
    @JsonProperty("reserve_price") val reservePrice: Long? = null,
    @field:NotNull
    @JsonProperty("start_time") val startTime: Instant? = null,
    @field:NotNull
    @JsonProperty("commit_end_time") val commitEndTime: Instant? = null,
    @field:NotNull
    @JsonProperty("reveal_end_time") val revealEndTime: Instant? = null
)

data class CreateAuctionResponse(
    @JsonProperty("auction_id") val auctionId: Uuid?,
    val title: String? = null,
    val type: AuctionType? = null,
    val sellerId: Uuid? = null,
    val startTime: Instant? = null,
    val commitEndTime: Instant? = null,
    val revealEndTime: Instant? = null,
    val status: String? = null,
    val message: String? = null
)

data class CommitBidRequest(
    @field:NotBlank
    @JsonProperty("blind_hash") val blindHash: String? = null,
    @field:NotNull
    @field:Positive
    @JsonProperty("lockup_deposit") val lockupDeposit: Long? = null
)

data class CommitBidResponse(
    @JsonProperty("bid_id") val bidId: Uuid?,
    @JsonProperty("auction_id") val auctionId: Uuid?,
    @JsonProperty("locked_deposit") val lockedDeposit: Long?,
    @JsonProperty("is_revealed") val isRevealed: Boolean? = null,
    val status: String?,
    val message: String? = null
)

data class RevealBidRequest(
    @field:NotNull
    @field:Positive
    @JsonProperty("actual_bid") val actualBid: Long? = null,
    @field:NotBlank
    @JsonProperty("secret_salt") val secretSalt: String? = null
)

data class RevealBidResponse(
    @JsonProperty("bid_id") val bidId: Uuid?,
    @JsonProperty("actual_bid") val actualBid: Long?,
    @JsonProperty("is_revealed") val isRevealed: Boolean? = null,
    val status: String?,
    val message: String? = null
)

data class AuctionDetailResponse(
    @JsonProperty("auction_id") val auctionId: Uuid?,
    @JsonProperty("seller_id") val sellerId: Uuid,
    val title: String,
    val type: AuctionType,
    val status: AuctionStatus,
    @JsonProperty("start_time") val startTime: Instant,
    @JsonProperty("end_time") val endTime: Instant,
    @JsonProperty("winner_id") val winnerId: Uuid?,
    @JsonProperty("final_price") val finalPrice: Long?,
    val version: Long,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("reserve_price") val reservePrice: Long,
    @JsonProperty("commit_end_time") val commitEndTime: Instant,
    @JsonProperty("reveal_end_time") val revealEndTime: Instant,
    @JsonProperty("second_bidder_id") val secondBidderId: Uuid? = null
)