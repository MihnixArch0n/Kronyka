package example.kronyka.presentation.controller

import com.ninjasquad.springmockk.MockkBean
import example.kronyka.domain.model.*
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.infrastructure.security.JwtTokenProvider
import example.kronyka.infrastructure.security.SecurityConfig
import example.kronyka.infrastructure.config.WebConfig
import example.kronyka.presentation.advice.GlobalExceptionHandler
import example.kronyka.presentation.dto.*
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@WebMvcTest(
    controllers = [AuctionController::class],
    excludeAutoConfiguration = [ExposedAutoConfiguration::class]
)
@AutoConfigureMockMvc(addFilters = true)
@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler::class, WebConfig::class, SecurityConfig::class, JwtTokenProvider::class)
class AuctionControllerTest {

    @MockkBean
    private lateinit var auctionService: AuctionService

    @MockkBean
    private lateinit var vickreyAuctionService: VickreyAuctionService

    @Autowired
    private lateinit var restTestClient: RestTestClient

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    private fun authorizationHeader(id: Uuid): String {
        val user = User(
            id = id,
            username = "test-user",
            passwordHash = "unused",
            fullName = "Test User",
            createdAt = Clock.System.now()
        )
        return "Bearer ${jwtTokenProvider.generateToken(user)}"
    }

    private fun sampleVickreyAuction(
        id: Uuid = Uuid.generateV7(),
        sellerId: Uuid = Uuid.generateV7(),
        title: String = "Rare Digital Asset",
        reservePrice: Long = 100_000L,
        status: AuctionStatus = AuctionStatus.COMMIT,
        startTime: Instant = Clock.System.now(),
        commitEndTime: Instant = Clock.System.now() + 1.hours,
        revealEndTime: Instant = Clock.System.now() + 2.hours
    ) = VickreyAuction(
        id = id,
        sellerId = sellerId,
        title = title,
        type = AuctionType.VICKREY,
        status = status,
        startTime = startTime,
        endTime = revealEndTime,
        winnerId = null,
        finalPrice = null,
        version = 1L,
        createdAt = startTime,
        reservePrice = reservePrice,
        commitEndTime = commitEndTime,
        revealEndTime = revealEndTime,
        secondBidderId = null
    )

    private fun sampleVickreyBid(
        id: Uuid = Uuid.generateV7(),
        auctionId: Uuid = Uuid.generateV7(),
        bidderId: Uuid = Uuid.generateV7(),
        blindHash: String = "a".repeat(64),
        lockupDeposit: Long = 200_000L,
        actualBid: Long? = null,
        isRevealed: Boolean = false,
        revealedAt: Instant? = null
    ) = VickreyBid(
        id = id,
        auctionId = auctionId,
        bidderId = bidderId,
        blindHash = blindHash,
        lockupDeposit = lockupDeposit,
        actualBid = actualBid,
        isRevealed = isRevealed,
        createdAt = Clock.System.now(),
        revealedAt = revealedAt
    )

    @Nested
    @DisplayName("POST /api/auctions - Create Vickrey Auction")
    inner class CreateVickreyAuctionTests {

        @Test
        fun `succeeds with 201 Created and wraps response in ApiResponse envelope`() {
            val sellerId = Uuid.generateV7()
            val auctionId = Uuid.generateV7()
            val now = Clock.System.now()
            val startTime = now + 1.hours
            val commitEndTime = now + 2.hours
            val revealEndTime = now + 3.hours

            val auction = sampleVickreyAuction(
                id = auctionId,
                sellerId = sellerId,
                title = "Antique Watch",
                reservePrice = 500_000L,
                startTime = startTime,
                commitEndTime = commitEndTime,
                revealEndTime = revealEndTime
            )

            every {
                vickreyAuctionService.createVickreyAuction(
                    sellerId = sellerId,
                    title = "Antique Watch",
                    reservePrice = 500_000L,
                    startTime = any<Instant>(),
                    commitEndTime = any<Instant>(),
                    revealEndTime = any<Instant>()
                )
            } returns auction

            val request = CreateVickreyAuctionRequest(
                title = "Antique Watch",
                reservePrice = 500_000L,
                startTime = startTime,
                commitEndTime = commitEndTime,
                revealEndTime = revealEndTime
            )

            val response = restTestClient.post()
                .uri("/api/auctions")
                .header("Authorization", authorizationHeader(sellerId))
                .header("X-User-Id", Uuid.generateV7().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody<ApiResponse<CreateAuctionResponse>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe 1000
                message.shouldBeNull()
                result.shouldNotBeNull()
                result.auctionId shouldBe auctionId
                result.title shouldBe "Antique Watch"
                result.type shouldBe AuctionType.VICKREY
                result.status shouldBe "COMMIT"
                result.sellerId shouldBe sellerId
            }

            verify(exactly = 1) {
                vickreyAuctionService.createVickreyAuction(
                    sellerId = sellerId,
                    title = "Antique Watch",
                    reservePrice = 500_000L,
                    startTime = any<Instant>(),
                    commitEndTime = any<Instant>(),
                    revealEndTime = any<Instant>()
                )
            }
        }

        @Test
        fun `fails with 401 Unauthorized when JWT is missing`() {
            val now = Clock.System.now()
            val request = CreateVickreyAuctionRequest(
                title = "Antique Watch",
                reservePrice = 500_000L,
                startTime = now + 1.hours,
                commitEndTime = now + 2.hours,
                revealEndTime = now + 3.hours
            )

            restTestClient.post()
                .uri("/api/auctions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `fails with 400 Bad Request when title is blank`() {
            val sellerId = Uuid.generateV7()
            val now = Clock.System.now()
            val request = CreateVickreyAuctionRequest(
                title = "   ",
                reservePrice = 500_000L,
                startTime = now + 1.hours,
                commitEndTime = now + 2.hours,
                revealEndTime = now + 3.hours
            )

            val response = restTestClient.post()
                .uri("/api/auctions")
                .header("Authorization", authorizationHeader(sellerId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.VALIDATION_FAILED.code
                result.shouldBeNull()
            }
        }

        @Test
        fun `fails with 400 Bad Request when reserve_price is negative`() {
            val sellerId = Uuid.generateV7()
            val now = Clock.System.now()
            val request = CreateVickreyAuctionRequest(
                title = "Antique Watch",
                reservePrice = -100L,
                startTime = now + 1.hours,
                commitEndTime = now + 2.hours,
                revealEndTime = now + 3.hours
            )

            val response = restTestClient.post()
                .uri("/api/auctions")
                .header("Authorization", authorizationHeader(sellerId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.VALIDATION_FAILED.code
            }
        }

        @Test
        fun `maps InvalidAuctionTimeException to 400 Bad Request with code 2011`() {
            val sellerId = Uuid.generateV7()
            val now = Clock.System.now()

            every {
                vickreyAuctionService.createVickreyAuction(
                    any<Uuid>(),
                    any<String>(),
                    any<Instant>(),
                    any<Long>(),
                    any<Instant>(),
                    any<Instant>()
                )
            } throws InvalidAuctionTimeException()

            val request = CreateVickreyAuctionRequest(
                title = "Antique Watch",
                reservePrice = 500_000L,
                startTime = now,
                commitEndTime = now + 2.hours,
                revealEndTime = now + 1.hours
            )

            val response = restTestClient.post()
                .uri("/api/auctions")
                .header("Authorization", authorizationHeader(sellerId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.INVALID_AUCTION_TIME.code
                message shouldBe ErrorCode.INVALID_AUCTION_TIME.message
            }
        }

        @Test
        fun `maps InvalidReservePriceException to 400 Bad Request with code 2010`() {
            val sellerId = Uuid.generateV7()
            val now = Clock.System.now()

            every {
                vickreyAuctionService.createVickreyAuction(
                    any<Uuid>(),
                    any<String>(),
                    any<Instant>(),
                    any<Long>(),
                    any<Instant>(),
                    any<Instant>()
                )
            } throws InvalidReservePriceException()

            val request = CreateVickreyAuctionRequest(
                title = "Antique Watch",
                reservePrice = 0L,
                startTime = now,
                commitEndTime = now + 1.hours,
                revealEndTime = now + 2.hours
            )

            val response = restTestClient.post()
                .uri("/api/auctions")
                .header("Authorization", authorizationHeader(sellerId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.INVALID_RESERVE_PRICE.code
                message shouldBe ErrorCode.INVALID_RESERVE_PRICE.message
            }
        }
    }

    @Nested
    @DisplayName("GET /api/auctions/{id} - Get Auction Detail")
    inner class GetAuctionDetailTests {

        @Test
        fun `returns 200 OK with total_commitments for Vickrey auction`() {
            val auctionId = Uuid.generateV7()
            val sellerId = Uuid.generateV7()
            val auction = sampleVickreyAuction(id = auctionId, sellerId = sellerId, reservePrice = 200_000L)

            every { auctionService.getAuction(auctionId) } returns auction
            every { vickreyAuctionService.getCommitmentCount(auctionId) } returns 5L

            val response = restTestClient.get()
                .uri("/api/auctions/{id}", auctionId)
                .exchange()
                .expectStatus().isOk
                .expectBody<ApiResponse<AuctionDetailResponse>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe 1000
                result.shouldNotBeNull()
                result.auctionId shouldBe auctionId
                result.sellerId shouldBe sellerId
                result.type shouldBe AuctionType.VICKREY
                result.status shouldBe AuctionStatus.COMMIT
                result.reservePrice shouldBe 200_000L
                result.totalCommitments shouldBe 5L
            }

            verify(exactly = 1) { auctionService.getAuction(auctionId) }
            verify(exactly = 1) { vickreyAuctionService.getCommitmentCount(auctionId) }
        }

        @Test
        fun `maps AuctionNotFoundException to 404 Not Found with code 2001`() {
            val auctionId = Uuid.generateV7()

            every { auctionService.getAuction(auctionId) } throws AuctionNotFoundException()

            val response = restTestClient.get()
                .uri("/api/auctions/{id}", auctionId)
                .exchange()
                .expectStatus().isNotFound
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.AUCTION_NOT_FOUND.code
                message shouldBe ErrorCode.AUCTION_NOT_FOUND.message
                result.shouldBeNull()
            }
        }
    }

    @Nested
    @DisplayName("DELETE /api/auctions/{id} - Cancel Auction")
    inner class CancelAuctionTests {

        @Test
        fun `succeeds with 200 OK when seller cancels auction`() {
            val auctionId = Uuid.generateV7()
            val sellerId = Uuid.generateV7()
            val cancelledAuction = sampleVickreyAuction(id = auctionId, sellerId = sellerId, status = AuctionStatus.CANCELLED)

            every { auctionService.cancelAuction(auctionId, sellerId) } returns cancelledAuction

            val response = restTestClient.delete()
                .uri("/api/auctions/{id}", auctionId)
                .header("Authorization", authorizationHeader(sellerId))
                .exchange()
                .expectStatus().isOk
                .expectBody<ApiResponse<CancelAuctionResponse>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe 1000
                result.shouldNotBeNull()
                result.auctionId shouldBe auctionId
                result.message shouldBe "Auction cancelled successfully"
            }

            verify(exactly = 1) { auctionService.cancelAuction(auctionId, sellerId) }
        }

        @Test
        fun `fails with 401 Unauthorized when JWT is missing`() {
            val auctionId = Uuid.generateV7()

            restTestClient.delete()
                .uri("/api/auctions/{id}", auctionId)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `maps UnauthorizedAuctionActionException to 403 Forbidden with code 2009`() {
            val auctionId = Uuid.generateV7()
            val intruderId = Uuid.generateV7()

            every {
                auctionService.cancelAuction(auctionId, intruderId)
            } throws UnauthorizedAuctionActionException()

            val response = restTestClient.delete()
                .uri("/api/auctions/{id}", auctionId)
                .header("Authorization", authorizationHeader(intruderId))
                .exchange()
                .expectStatus().isForbidden
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.UNAUTHORIZED_ACTION.code
            }
        }

        @Test
        fun `maps AuctionCancellationException to 409 Conflict with code 2004`() {
            val auctionId = Uuid.generateV7()
            val sellerId = Uuid.generateV7()

            every {
                auctionService.cancelAuction(auctionId, sellerId)
            } throws AuctionCancellationException()

            val response = restTestClient.delete()
                .uri("/api/auctions/{id}", auctionId)
                .header("Authorization", authorizationHeader(sellerId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.AUCTION_CANCELLATION_FAILED.code
            }
        }

        @Test
        fun `maps InvalidAuctionStateException to 400 Bad Request with code 2006`() {
            val auctionId = Uuid.generateV7()
            val sellerId = Uuid.generateV7()

            every {
                auctionService.cancelAuction(auctionId, sellerId)
            } throws InvalidAuctionStateException("Auction canceled or settled")

            val response = restTestClient.delete()
                .uri("/api/auctions/{id}", auctionId)
                .header("Authorization", authorizationHeader(sellerId))
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.INVALID_AUCTION_STATE.code
            }
        }
    }

    @Nested
    @DisplayName("POST /api/auctions/{id}/commit - Commit Bid")
    inner class CommitBidTests {

        @Test
        fun `succeeds with 201 Created and COMMITTED status`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()
            val bidId = Uuid.generateV7()
            val blindHash = "e7a8b92c63d4f107389a456bcdef123456789abcdef0123456789abcdef01234"
            val lockupDeposit = 300_000L

            val bid = sampleVickreyBid(
                id = bidId,
                auctionId = auctionId,
                bidderId = bidderId,
                blindHash = blindHash,
                lockupDeposit = lockupDeposit
            )

            every {
                vickreyAuctionService.commitBid(
                    auctionId = auctionId,
                    bidderId = bidderId,
                    blindHash = blindHash,
                    lockupDeposit = lockupDeposit
                )
            } returns bid

            val request = CommitBidRequest(
                blindHash = blindHash,
                lockupDeposit = lockupDeposit
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/commit", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody<ApiResponse<CommitBidResponse>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe 1000
                result.shouldNotBeNull()
                result.bidId shouldBe bidId
                result.auctionId shouldBe auctionId
                result.lockedDeposit shouldBe lockupDeposit
                result.isRevealed shouldBe false
                result.status shouldBe "COMMITTED"
                result.message shouldBe "Blind commitment recorded."
            }

            verify(exactly = 1) {
                vickreyAuctionService.commitBid(
                    auctionId = auctionId,
                    bidderId = bidderId,
                    blindHash = blindHash,
                    lockupDeposit = lockupDeposit
                )
            }
        }

        @Test
        fun `fails with 400 Bad Request when blind_hash is blank`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()
            val request = CommitBidRequest(
                blindHash = "",
                lockupDeposit = 100_000L
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/commit", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.VALIDATION_FAILED.code
            }
        }

        @Test
        fun `fails with 400 Bad Request when blind_hash has invalid format or length`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()
            val request = CommitBidRequest(
                blindHash = "invalid_hash_too_short",
                lockupDeposit = 100_000L
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/commit", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.VALIDATION_FAILED.code
            }
        }

        @Test
        fun `fails with 400 Bad Request when lockup_deposit is zero or negative`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()
            val request = CommitBidRequest(
                blindHash = "e7a8b92c63d4f107389a456bcdef123456789abcdef0123456789abcdef01234",
                lockupDeposit = 0L
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/commit", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.VALIDATION_FAILED.code
            }
        }

        @Test
        fun `maps InvalidAuctionStateException to 400 Bad Request with code 2006`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()

            every {
                vickreyAuctionService.commitBid(any<Uuid>(), any<Uuid>(), any<String>(), any<Long>())
            } throws InvalidAuctionStateException("Auction is not in COMMIT phase")

            val request = CommitBidRequest(
                blindHash = "e7a8b92c63d4f107389a456bcdef123456789abcdef0123456789abcdef01234",
                lockupDeposit = 100_000L
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/commit", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.INVALID_AUCTION_STATE.code
            }
        }

        @Test
        fun `maps InvalidDepositException to 400 Bad Request with code 2008`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()

            every {
                vickreyAuctionService.commitBid(any<Uuid>(), any<Uuid>(), any<String>(), any<Long>())
            } throws InvalidDepositException()

            val request = CommitBidRequest(
                blindHash = "e7a8b92c63d4f107389a456bcdef123456789abcdef0123456789abcdef01234",
                lockupDeposit = 50_000L
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/commit", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.INVALID_DEPOSIT.code
                message shouldBe ErrorCode.INVALID_DEPOSIT.message
            }
        }
    }

    @Nested
    @DisplayName("POST /api/auctions/{id}/reveal - Reveal Bid")
    inner class RevealBidTests {

        @Test
        fun `succeeds with 200 OK and REVEALED_VALID status`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()
            val bidId = Uuid.generateV7()
            val actualBid = 250_000L
            val secretSalt = "bidder_secret_salt_xyz"

            val revealedBid = sampleVickreyBid(
                id = bidId,
                auctionId = auctionId,
                bidderId = bidderId,
                actualBid = actualBid,
                isRevealed = true,
                revealedAt = Clock.System.now()
            )

            every {
                vickreyAuctionService.revealBid(
                    auctionId = auctionId,
                    bidderId = bidderId,
                    actualBid = actualBid,
                    secretSalt = secretSalt
                )
            } returns revealedBid

            val request = RevealBidRequest(
                actualBid = actualBid,
                secretSalt = secretSalt
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/reveal", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk
                .expectBody<ApiResponse<RevealBidResponse>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe 1000
                result.shouldNotBeNull()
                result.bidId shouldBe bidId
                result.actualBid shouldBe actualBid
                result.isRevealed shouldBe true
                result.status shouldBe "REVEALED_VALID"
                result.message shouldBe "Bid revealed successfully."
            }

            verify(exactly = 1) {
                vickreyAuctionService.revealBid(
                    auctionId = auctionId,
                    bidderId = bidderId,
                    actualBid = actualBid,
                    secretSalt = secretSalt
                )
            }
        }

        @Test
        fun `fails with 400 Bad Request when secret_salt is blank`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()
            val request = RevealBidRequest(
                actualBid = 250_000L,
                secretSalt = "   "
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/reveal", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.VALIDATION_FAILED.code
            }
        }

        @Test
        fun `fails with 400 Bad Request when actual_bid is negative or zero`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()
            val request = RevealBidRequest(
                actualBid = 0L,
                secretSalt = "salt"
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/reveal", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.VALIDATION_FAILED.code
            }
        }

        @Test
        fun `maps BidNotFoundException to 404 Not Found with code 2005`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()

            every {
                vickreyAuctionService.revealBid(any<Uuid>(), any<Uuid>(), any<Long>(), any<String>())
            } throws BidNotFoundException()

            val request = RevealBidRequest(
                actualBid = 250_000L,
                secretSalt = "salt"
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/reveal", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isNotFound
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.BID_NOT_FOUND.code
                message shouldBe ErrorCode.BID_NOT_FOUND.message
            }
        }

        @Test
        fun `maps RevealMismatchException to 400 Bad Request with code 2003`() {
            val auctionId = Uuid.generateV7()
            val bidderId = Uuid.generateV7()

            every {
                vickreyAuctionService.revealBid(any<Uuid>(), any<Uuid>(), any<Long>(), any<String>())
            } throws RevealMismatchException()

            val request = RevealBidRequest(
                actualBid = 250_000L,
                secretSalt = "wrong_salt"
            )

            val response = restTestClient.post()
                .uri("/api/auctions/{id}/reveal", auctionId)
                .header("Authorization", authorizationHeader(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody<ApiResponse<Unit>>()
                .returnResult()
                .responseBody

            assertSoftly(response) {
                shouldNotBeNull()
                code shouldBe ErrorCode.REVEAL_MISMATCH.code
                message shouldBe ErrorCode.REVEAL_MISMATCH.message
            }
        }
    }
}
