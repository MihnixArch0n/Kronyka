package example.kronyka.presentation.dto

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.infrastructure.config.WebConfig
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

@DisplayName("Auction DTOs kotlinx.serialization Tests")
class AuctionSerializationTest {

    private val json: Json = WebConfig().json()

    @Test
    fun `serializes and deserializes CreateVickreyAuctionRequest with ISO Instant timestamps and snake_case keys`() {
        val startTime = Instant.parse("2026-10-22T10:00:00Z")
        val commitEndTime = Instant.parse("2026-10-22T12:00:00Z")
        val revealEndTime = Instant.parse("2026-10-22T14:00:00Z")

        val request = CreateVickreyAuctionRequest(
            title = "Vintage Watch",
            reservePrice = 1_500_000L,
            startTime = startTime,
            commitEndTime = commitEndTime,
            revealEndTime = revealEndTime
        )

        val jsonString = json.encodeToString(request)
        jsonString shouldContain "\"title\":\"Vintage Watch\""
        jsonString shouldContain "\"reserve_price\":1500000"
        jsonString shouldContain "\"start_time\":\"2026-10-22T10:00:00Z\""
        jsonString shouldContain "\"commit_end_time\":\"2026-10-22T12:00:00Z\""
        jsonString shouldContain "\"reveal_end_time\":\"2026-10-22T14:00:00Z\""

        val decoded = json.decodeFromString<CreateVickreyAuctionRequest>(jsonString)
        decoded.title shouldBe "Vintage Watch"
        decoded.reservePrice shouldBe 1_500_000L
        decoded.startTime shouldBe startTime
        decoded.commitEndTime shouldBe commitEndTime
        decoded.revealEndTime shouldBe revealEndTime
    }

    @Test
    fun `serializes and deserializes CreateAuctionResponse with Kotlin Uuid and Instant`() {
        val auctionId = Uuid.parse("0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
        val sellerId = Uuid.parse("0192e2b3-7a20-72c0-8a43-02f5e3ef54aa")
        val startTime = Instant.parse("2026-10-22T08:00:00Z")

        val response = CreateAuctionResponse(
            auctionId = auctionId,
            title = "Antique Clock",
            type = AuctionType.VICKREY,
            sellerId = sellerId,
            startTime = startTime,
            status = "COMMIT",
            message = "Created"
        )

        val jsonString = json.encodeToString(response)
        jsonString shouldContain "\"auction_id\":\"0192e2b3-7a20-72c0-8a43-02f5e3ef5499\""
        jsonString shouldContain "\"seller_id\":\"0192e2b3-7a20-72c0-8a43-02f5e3ef54aa\""
        jsonString shouldContain "\"type\":\"VICKREY\""

        val decoded = json.decodeFromString<CreateAuctionResponse>(jsonString)
        decoded.auctionId shouldBe auctionId
        decoded.sellerId shouldBe sellerId
        decoded.type shouldBe AuctionType.VICKREY
    }

    @Test
    fun `serializes and deserializes AuctionDetailResponse wrapped in ApiResponse`() {
        val auctionId = Uuid.parse("0192e2b3-7a20-72c0-8a43-02f5e3ef5401")
        val sellerId = Uuid.parse("0192e2b3-7a20-72c0-8a43-02f5e3ef5402")
        val now = Instant.parse("2026-10-22T12:00:00Z")

        val detail = AuctionDetailResponse(
            auctionId = auctionId,
            sellerId = sellerId,
            title = "Collector Coin",
            type = AuctionType.VICKREY,
            status = AuctionStatus.COMMIT,
            startTime = now,
            endTime = now,
            winnerId = null,
            finalPrice = null,
            version = 1L,
            createdAt = now,
            reservePrice = 100_000L,
            totalCommitments = 5L
        )

        val envelope = ApiResponse.success(detail)
        val jsonString = json.encodeToString(envelope)

        jsonString shouldContain "\"code\":1000"
        jsonString shouldContain "\"auction_id\":\"0192e2b3-7a20-72c0-8a43-02f5e3ef5401\""
        jsonString shouldContain "\"status\":\"COMMIT\""
        jsonString shouldContain "\"type\":\"VICKREY\""

        val decoded = json.decodeFromString<ApiResponse<AuctionDetailResponse>>(jsonString)
        decoded.code shouldBe 1000
        val result = decoded.result.shouldNotBeNull()
        result.auctionId shouldBe auctionId
        result.status shouldBe AuctionStatus.COMMIT
        result.type shouldBe AuctionType.VICKREY
        result.totalCommitments shouldBe 5L
    }

    @Test
    fun `serializes and deserializes CommitBidRequest and CommitBidResponse`() {
        val bidId = Uuid.parse("0192e2b3-7a20-72c0-8a43-02f5e3ef5403")
        val auctionId = Uuid.parse("0192e2b3-7a20-72c0-8a43-02f5e3ef5404")
        val blindHash = "a".repeat(64)

        val req = CommitBidRequest(blindHash = blindHash, lockupDeposit = 500_000L)
        val reqJson = json.encodeToString(req)
        reqJson shouldContain "\"blind_hash\":\"$blindHash\""
        reqJson shouldContain "\"lockup_deposit\":500000"

        val decodedReq = json.decodeFromString<CommitBidRequest>(reqJson)
        decodedReq.blindHash shouldBe blindHash
        decodedReq.lockupDeposit shouldBe 500_000L

        val res = CommitBidResponse(
            bidId = bidId,
            auctionId = auctionId,
            lockedDeposit = 500_000L,
            isRevealed = false,
            status = "COMMITTED",
            message = "Blind commitment recorded."
        )
        val resJson = json.encodeToString(res)
        resJson shouldContain "\"bid_id\":\"$bidId\""
        resJson shouldContain "\"locked_deposit\":500000"

        val decodedRes = json.decodeFromString<CommitBidResponse>(resJson)
        decodedRes.bidId shouldBe bidId
        decodedRes.isRevealed shouldBe false
    }

    @Test
    fun `serializes and deserializes RevealBidRequest and RevealBidResponse`() {
        val bidId = Uuid.parse("0192e2b3-7a20-72c0-8a43-02f5e3ef5405")
        val req = RevealBidRequest(actualBid = 450_000L, secretSalt = "s3cr3t-salt-123")
        val reqJson = json.encodeToString(req)
        reqJson shouldContain "\"actual_bid\":450000"
        reqJson shouldContain "\"secret_salt\":\"s3cr3t-salt-123\""

        val decodedReq = json.decodeFromString<RevealBidRequest>(reqJson)
        decodedReq.actualBid shouldBe 450_000L
        decodedReq.secretSalt shouldBe "s3cr3t-salt-123"

        val res = RevealBidResponse(
            bidId = bidId,
            actualBid = 450_000L,
            isRevealed = true,
            status = "REVEALED_VALID",
            message = "Bid revealed successfully."
        )
        val resJson = json.encodeToString(res)
        resJson shouldContain "\"bid_id\":\"$bidId\""
        resJson shouldContain "\"actual_bid\":450000"
        resJson shouldContain "\"is_revealed\":true"

        val decodedRes = json.decodeFromString<RevealBidResponse>(resJson)
        decodedRes.bidId shouldBe bidId
        decodedRes.actualBid shouldBe 450_000L
        decodedRes.isRevealed shouldBe true
    }
}
