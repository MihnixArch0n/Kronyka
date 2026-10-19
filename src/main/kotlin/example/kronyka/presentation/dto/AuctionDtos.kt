package example.kronyka.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.model.VickreyBid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import kotlin.time.Instant
import kotlin.uuid.Uuid

fun VickreyAuction.toCreateResponse() = CreateAuctionResponse(
    auctionId = id,
    title = title,
    type = type,
    sellerId = sellerId,
    startTime = startTime,
    commitEndTime = commitEndTime,
    revealEndTime = revealEndTime,
    endTime = endTime,
    status = status.name
)

fun Auction.toDetailResponse(totalCommitments: Long? = null) = AuctionDetailResponse(
    auctionId = id,
    sellerId = sellerId,
    title = title,
    type = type,
    status = (this as? VickreyAuction)?.resolveStatus() ?: status,
    startTime = startTime,
    endTime = endTime,
    winnerId = winnerId,
    finalPrice = finalPrice,
    version = version,
    createdAt = createdAt,
    reservePrice = (this as? VickreyAuction)?.reservePrice,
    commitEndTime = (this as? VickreyAuction)?.commitEndTime,
    revealEndTime = (this as? VickreyAuction)?.revealEndTime,
    totalCommitments = totalCommitments
)

fun VickreyBid.toCommitResponse() = CommitBidResponse(
    bidId = id,
    auctionId = auctionId,
    lockedDeposit = lockupDeposit,
    isRevealed = isRevealed,
    status = "COMMITTED",
    message = "Blind commitment recorded."
)

fun VickreyBid.toRevealResponse() = RevealBidResponse(
    bidId = id,
    actualBid = actualBid,
    isRevealed = isRevealed,
    status = "REVEALED_VALID",
    message = "Bid revealed successfully."
)

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
    @JsonProperty("seller_id") val sellerId: Uuid? = null,
    @JsonProperty("start_time") val startTime: Instant? = null,
    @JsonProperty("commit_end_time") val commitEndTime: Instant? = null,
    @JsonProperty("reveal_end_time") val revealEndTime: Instant? = null,
    @JsonProperty("end_time") val endTime: Instant? = null,
    val status: String? = null,
    val message: String? = null
)

data class CancelAuctionResponse(
    @JsonProperty("auction_id") val auctionId: Uuid,
    val message: String = "Auction cancelled successfully"
)

data class CommitBidRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "^[a-fA-F0-9]{64}$",
        message = "Blind hash must be a 64-character hexadecimal SHA-256 string"
    )
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
    val status: String? = null,
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
    val status: String? = null,
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
    @JsonProperty("reserve_price") val reservePrice: Long? = null,
    @JsonProperty("commit_end_time") val commitEndTime: Instant? = null,
    @JsonProperty("reveal_end_time") val revealEndTime: Instant? = null,
    @JsonProperty("second_bidder_id") val secondBidderId: Uuid? = null,
    @JsonProperty("total_commitments") val totalCommitments: Long? = null
)