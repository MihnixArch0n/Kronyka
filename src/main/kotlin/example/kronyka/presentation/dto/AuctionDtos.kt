package example.kronyka.presentation.dto

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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant
import kotlin.uuid.Uuid

object AuctionStatusSerializer : KSerializer<AuctionStatus> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AuctionStatus", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: AuctionStatus) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): AuctionStatus = AuctionStatus.valueOf(decoder.decodeString())
}

object AuctionTypeSerializer : KSerializer<AuctionType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AuctionType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: AuctionType) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): AuctionType = AuctionType.valueOf(decoder.decodeString())
}

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
    secondBidderId = (this as? VickreyAuction)?.secondBidderId,
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

@Serializable
data class AuctionPageResponse(
    val total: Long,
    val items: List<AuctionDetailResponse>
)

@Serializable
data class CreateVickreyAuctionRequest(
    @field:NotBlank val title: String? = null,
    @field:NotNull
    @field:PositiveOrZero
    @SerialName("reserve_price") val reservePrice: Long? = null,
    @field:NotNull
    @SerialName("start_time") val startTime: Instant? = null,
    @field:NotNull
    @SerialName("commit_end_time") val commitEndTime: Instant? = null,
    @field:NotNull
    @SerialName("reveal_end_time") val revealEndTime: Instant? = null
)

@Serializable
data class CreateAuctionResponse(
    @SerialName("auction_id") val auctionId: Uuid?,
    val title: String? = null,
    @Serializable(with = AuctionTypeSerializer::class)
    val type: AuctionType? = null,
    @SerialName("seller_id") val sellerId: Uuid? = null,
    @SerialName("start_time") val startTime: Instant? = null,
    @SerialName("commit_end_time") val commitEndTime: Instant? = null,
    @SerialName("reveal_end_time") val revealEndTime: Instant? = null,
    @SerialName("end_time") val endTime: Instant? = null,
    val status: String? = null,
    val message: String? = null
)

@Serializable
data class CancelAuctionResponse(
    @SerialName("auction_id") val auctionId: Uuid,
    val message: String = "Auction cancelled successfully"
)

@Serializable
data class CommitBidRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "^[a-fA-F0-9]{64}$",
        message = "Blind hash must be a 64-character hexadecimal SHA-256 string"
    )
    @SerialName("blind_hash") val blindHash: String? = null,
    @field:NotNull
    @field:Positive
    @SerialName("lockup_deposit") val lockupDeposit: Long? = null
)

@Serializable
data class CommitBidResponse(
    @SerialName("bid_id") val bidId: Uuid?,
    @SerialName("auction_id") val auctionId: Uuid?,
    @SerialName("locked_deposit") val lockedDeposit: Long?,
    @SerialName("is_revealed") val isRevealed: Boolean? = null,
    val status: String? = null,
    val message: String? = null
)

@Serializable
data class RevealBidRequest(
    @field:NotNull
    @field:Positive
    @SerialName("actual_bid") val actualBid: Long? = null,
    @field:NotBlank
    @SerialName("secret_salt") val secretSalt: String? = null
)

@Serializable
data class RevealBidResponse(
    @SerialName("bid_id") val bidId: Uuid?,
    @SerialName("actual_bid") val actualBid: Long?,
    @SerialName("is_revealed") val isRevealed: Boolean? = null,
    val status: String? = null,
    val message: String? = null
)

@Serializable
data class AuctionDetailResponse(
    @SerialName("auction_id") val auctionId: Uuid?,
    @SerialName("seller_id") val sellerId: Uuid,
    val title: String,
    @Serializable(with = AuctionTypeSerializer::class)
    val type: AuctionType,
    @Serializable(with = AuctionStatusSerializer::class)
    val status: AuctionStatus,
    @SerialName("start_time") val startTime: Instant,
    @SerialName("end_time") val endTime: Instant,
    @SerialName("winner_id") val winnerId: Uuid?,
    @SerialName("final_price") val finalPrice: Long?,
    val version: Long,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("reserve_price") val reservePrice: Long? = null,
    @SerialName("commit_end_time") val commitEndTime: Instant? = null,
    @SerialName("reveal_end_time") val revealEndTime: Instant? = null,
    @SerialName("second_bidder_id") val secondBidderId: Uuid? = null,
    @SerialName("total_commitments") val totalCommitments: Long? = null
)