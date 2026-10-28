package example.kronyka.presentation.dto

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.DutchAuction
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.model.VickreyBid
import io.swagger.v3.oas.annotations.media.Schema
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
    totalCommitments = totalCommitments,
    startPrice = (this as? DutchAuction)?.startPrice,
    floorPrice = (this as? DutchAuction)?.floorPrice,
    stepDecrement = (this as? DutchAuction)?.stepDecrement,
    stepIntervalSeconds = (this as? DutchAuction)?.stepIntervalSeconds
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

@Schema(description = "Paginated list of auctions")
@Serializable
data class AuctionPageResponse(
    @field:Schema(description = "Total number of auctions matching query filters", example = "42")
    val total: Long,

    @field:Schema(description = "List of auction details on the current page")
    val items: List<AuctionDetailResponse>
)

@Schema(description = "Request payload for creating a sealed-bid Vickrey auction")
@Serializable
data class CreateVickreyAuctionRequest(
    @field:Schema(description = "Title or name of the item being auctioned", example = "Rare Digital Collectible #001")
    @field:NotBlank val title: String? = null,

    @field:Schema(description = "Minimum acceptable price in VNĐ", example = "500000", minimum = "0")
    @field:NotNull
    @field:PositiveOrZero
    @SerialName("reserve_price") val reservePrice: Long? = null,

    @field:Schema(description = "Auction start timestamp (ISO-8601)", example = "2026-10-01T08:00:00Z")
    @field:NotNull
    @SerialName("start_time") val startTime: Instant? = null,

    @field:Schema(description = "Deadline timestamp for submitting blind commitments (Phase 1)", example = "2026-10-01T09:00:00Z")
    @field:NotNull
    @SerialName("commit_end_time") val commitEndTime: Instant? = null,

    @field:Schema(description = "Deadline timestamp for revealing secret bids (Phase 2)", example = "2026-10-01T09:30:00Z")
    @field:NotNull
    @SerialName("reveal_end_time") val revealEndTime: Instant? = null
)

@Schema(description = "Auction creation acknowledgement response")
@Serializable
data class CreateAuctionResponse(
    @field:Schema(description = "Unique auction identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("auction_id") val auctionId: Uuid?,

    @field:Schema(description = "Auction title", example = "Rare Digital Collectible #001")
    val title: String? = null,

    @field:Schema(description = "Auction engine type", example = "VICKREY")
    @Serializable(with = AuctionTypeSerializer::class)
    val type: AuctionType? = null,

    @field:Schema(description = "Seller's user identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("seller_id") val sellerId: Uuid? = null,

    @field:Schema(description = "Auction start time (ISO-8601)", example = "2026-10-01T08:00:00Z")
    @SerialName("start_time") val startTime: Instant? = null,

    @field:Schema(description = "Commit phase deadline", example = "2026-10-01T09:00:00Z")
    @SerialName("commit_end_time") val commitEndTime: Instant? = null,

    @field:Schema(description = "Reveal phase deadline", example = "2026-10-01T09:30:00Z")
    @SerialName("reveal_end_time") val revealEndTime: Instant? = null,

    @field:Schema(description = "Final auction conclusion deadline", example = "2026-10-01T09:30:00Z")
    @SerialName("end_time") val endTime: Instant? = null,

    @field:Schema(description = "Initial auction status", example = "COMMIT")
    val status: String? = null,

    @field:Schema(description = "Informational status message", example = "Auction created successfully")
    val message: String? = null
)

@Schema(description = "Cancellation confirmation response")
@Serializable
data class CancelAuctionResponse(
    @field:Schema(description = "Cancelled auction identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("auction_id") val auctionId: Uuid,

    @field:Schema(description = "Cancellation message", example = "Auction cancelled successfully")
    val message: String = "Auction cancelled successfully"
)

@Schema(description = "Vickrey Phase 1: Blind commitment payload")
@Serializable
data class CommitBidRequest(
    @field:Schema(
        description = "Hexadecimal SHA-256 hash of actual_bid + '_' + secret_salt (64 characters)",
        example = "e7a8b92c63d4f107389a456bcdef123456789abcdef0123456789abcdef01234"
    )
    @field:NotBlank
    @field:Pattern(
        regexp = "^[A-Fa-f0-9]{64}$",
        message = "blind_hash must be a valid 64-character lowercase or uppercase hex string"
    )
    @SerialName("blind_hash") val blindHash: String? = null,

    @field:Schema(
        description = "Deposit locked upfront in VNĐ (must be >= reserve_price and >= actual_bid)",
        example = "5000000",
        minimum = "1"
    )
    @field:NotNull
    @field:Positive
    @SerialName("lockup_deposit") val lockupDeposit: Long? = null
)

@Schema(description = "Vickrey Phase 1: Blind commitment acknowledgement")
@Serializable
data class CommitBidResponse(
    @field:Schema(description = "Unique bid identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("bid_id") val bidId: Uuid?,

    @field:Schema(description = "Associated auction identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("auction_id") val auctionId: Uuid?,

    @field:Schema(description = "Amount of VNĐ deposit locked in escrow", example = "5000000")
    @SerialName("locked_deposit") val lockedDeposit: Long?,

    @field:Schema(description = "Whether the bid has been revealed yet", example = "false")
    @SerialName("is_revealed") val isRevealed: Boolean? = null,

    @field:Schema(description = "Commitment status", example = "COMMITTED")
    val status: String? = null,

    @field:Schema(description = "Informational status message", example = "Blind commitment recorded.")
    val message: String? = null
)

@Schema(description = "Vickrey Phase 2: Reveal bid payload")
@Serializable
data class RevealBidRequest(
    @field:Schema(description = "Actual bid amount in VNĐ committed in Phase 1", example = "3200000", minimum = "1")
    @field:NotNull
    @field:Positive
    @SerialName("actual_bid") val actualBid: Long? = null,

    @field:Schema(description = "Secret salt used in the SHA-256 hash generation", example = "alice_secret_salt_xyz999")
    @field:NotBlank
    @SerialName("secret_salt") val secretSalt: String? = null
)

@Schema(description = "Vickrey Phase 2: Bid verification acknowledgement")
@Serializable
data class RevealBidResponse(
    @field:Schema(description = "Unique bid identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("bid_id") val bidId: Uuid?,

    @field:Schema(description = "Verified actual bid amount in VNĐ", example = "3200000")
    @SerialName("actual_bid") val actualBid: Long?,

    @field:Schema(description = "Reveal state", example = "true")
    @SerialName("is_revealed") val isRevealed: Boolean? = null,

    @field:Schema(description = "Bid verification status", example = "REVEALED_VALID")
    val status: String? = null,

    @field:Schema(description = "Informational verification message", example = "Bid revealed successfully.")
    val message: String? = null
)

@Schema(description = "Detailed auction representation")
@Serializable
data class AuctionDetailResponse(
    @field:Schema(description = "Auction unique identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("auction_id") val auctionId: Uuid?,

    @field:Schema(description = "Seller user identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("seller_id") val sellerId: Uuid,

    @field:Schema(description = "Auction title", example = "Rare Digital Collectible #001")
    val title: String,

    @field:Schema(description = "Auction type", example = "VICKREY")
    @Serializable(with = AuctionTypeSerializer::class)
    val type: AuctionType,

    @field:Schema(description = "Current lifecycle status", example = "COMMIT")
    @Serializable(with = AuctionStatusSerializer::class)
    val status: AuctionStatus,

    @field:Schema(description = "Auction start time (ISO-8601)", example = "2026-10-01T08:00:00Z")
    @SerialName("start_time") val startTime: Instant,

    @field:Schema(description = "Auction end time (ISO-8601)", example = "2026-10-01T09:30:00Z")
    @SerialName("end_time") val endTime: Instant,

    @field:Schema(description = "Winning bidder UUID if settled, null otherwise", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("winner_id") val winnerId: Uuid?,

    @field:Schema(description = "Settled sale price in VNĐ (second-highest price), null if unsold", example = "3000000")
    @SerialName("final_price") val finalPrice: Long?,

    @field:Schema(description = "Concurrency version number for optimistic locking", example = "1")
    val version: Long,

    @field:Schema(description = "Record creation timestamp", example = "2026-10-01T07:55:00Z")
    @SerialName("created_at") val createdAt: Instant,

    @field:Schema(description = "Minimum reserve price in VNĐ (Vickrey)", example = "500000")
    @SerialName("reserve_price") val reservePrice: Long? = null,

    @field:Schema(description = "Commit window deadline timestamp", example = "2026-10-01T09:00:00Z")
    @SerialName("commit_end_time") val commitEndTime: Instant? = null,

    @field:Schema(description = "Reveal window deadline timestamp", example = "2026-10-01T09:30:00Z")
    @SerialName("reveal_end_time") val revealEndTime: Instant? = null,

    @field:Schema(description = "Second highest bidder UUID (price setter) if settled", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("second_bidder_id") val secondBidderId: Uuid? = null,

    @field:Schema(description = "Count of total blind commitments submitted (only visible during COMMIT/REVEAL)", example = "14")
    @SerialName("total_commitments") val totalCommitments: Long? = null,

    @field:Schema(description = "Starting price in VNĐ (Dutch)", example = "10000000")
    @SerialName("start_price") val startPrice: Long? = null,

    @field:Schema(description = "Floor reserve price in VNĐ (Dutch)", example = "5000000")
    @SerialName("floor_price") val floorPrice: Long? = null,

    @field:Schema(description = "Price decrement amount in VNĐ per interval (Dutch)", example = "500000")
    @SerialName("step_decrement") val stepDecrement: Long? = null,

    @field:Schema(description = "Interval in seconds between price drops (Dutch)", example = "60")
    @SerialName("step_interval_seconds") val stepIntervalSeconds: Long? = null
)

@Schema(description = "Payload to initiate a new descending-price Dutch auction")
@Serializable
data class CreateDutchAuctionRequest(
    @field:Schema(description = "Dutch auction title", example = "Vintage Mechanical Keyboard")
    @field:NotBlank
    val title: String? = null,

    @field:Schema(description = "Starting price in VNĐ", example = "10000000", minimum = "1")
    @field:NotNull
    @field:Positive
    @SerialName("start_price")
    val startPrice: Long? = null,

    @field:Schema(description = "Floor minimum reserve price in VNĐ", example = "5000000", minimum = "0")
    @field:NotNull
    @field:PositiveOrZero
    @SerialName("floor_price")
    val floorPrice: Long? = null,

    @field:Schema(description = "Amount deducted per step interval in VNĐ", example = "500000", minimum = "1")
    @field:NotNull
    @field:Positive
    @SerialName("step_decrement")
    val stepDecrement: Long? = null,

    @field:Schema(description = "Interval in seconds between price reductions", example = "60", minimum = "1")
    @field:NotNull
    @field:Positive
    @SerialName("step_interval_seconds")
    val stepIntervalSeconds: Long? = null,

    @field:Schema(description = "Auction scheduled start time (ISO-8601)", example = "2026-10-01T08:00:00Z")
    @field:NotNull
    @SerialName("start_time")
    val startTime: Instant? = null,

    @field:Schema(description = "Auction concluding deadline (ISO-8601)", example = "2026-10-01T10:00:00Z")
    @field:NotNull
    @SerialName("end_time")
    val endTime: Instant? = null
)

@Schema(description = "Real-time calculated price for a Dutch auction")
@Serializable
data class DutchPriceResponse(
    @field:Schema(description = "Unique auction identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("auction_id")
    val auctionId: Uuid,

    @field:Schema(description = "Current effective price in VNĐ", example = "8500000")
    @SerialName("current_price")
    val currentPrice: Long
)

@Schema(description = "Instant buy purchase confirmation for a Dutch auction")
@Serializable
data class DutchBuyResponse(
    @field:Schema(description = "Purchased auction identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("auction_id")
    val auctionId: Uuid,

    @field:Schema(description = "Buyer user identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @SerialName("winner_id")
    val winnerId: Uuid,

    @field:Schema(description = "Final purchase price in VNĐ", example = "8500000")
    @SerialName("final_price")
    val finalPrice: Long,

    @field:Schema(description = "Resulting auction status", example = "SOLD")
    val status: String
)

fun DutchAuction.toDutchBuyResponse() = DutchBuyResponse(
    auctionId = id,
    winnerId = requireNotNull(winnerId),
    finalPrice = requireNotNull(finalPrice),
    status = status.name
)
