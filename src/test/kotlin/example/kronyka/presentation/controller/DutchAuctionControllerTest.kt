package example.kronyka.presentation.controller

import com.ninjasquad.springmockk.MockkBean
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.DutchAuction
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.DutchAuctionService
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.infrastructure.config.WebConfig
import example.kronyka.infrastructure.security.JwtTokenProvider
import example.kronyka.infrastructure.security.SecurityConfig
import example.kronyka.presentation.advice.GlobalExceptionHandler
import io.mockk.every
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@WebMvcTest(
    controllers = [AuctionController::class],
    excludeAutoConfiguration = [ExposedAutoConfiguration::class]
)
@AutoConfigureMockMvc(addFilters = true)
@Import(
    GlobalExceptionHandler::class,
    WebConfig::class,
    SecurityConfig::class
)
class DutchAuctionControllerTest {

    @MockkBean
    private lateinit var auctionService: AuctionService

    @MockkBean
    private lateinit var vickreyAuctionService: VickreyAuctionService

    @MockkBean
    private lateinit var dutchAuctionService: DutchAuctionService

    @MockkBean
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val userId =
        Uuid.parse("550e8400-e29b-41d4-a716-446655440000")

    private val token = "test-valid-jwt"

    private fun sampleDutchAuction(
        id: Uuid = Uuid.generateV7(),
        sellerId: Uuid = Uuid.generateV7(),
        winnerId: Uuid? = null,
        finalPrice: Long? = null,
        status: AuctionStatus = AuctionStatus.ACTIVE
    ) = DutchAuction(
        id = id,
        sellerId = sellerId,
        title = "Dutch Test Auction",
        type = AuctionType.DUTCH,
        status = status,
        startTime = Clock.System.now(),
        endTime = Clock.System.now() + 2.hours,
        winnerId = winnerId,
        finalPrice = finalPrice,
        version = if (status == AuctionStatus.SOLD) 1L else 0L,
        createdAt = Clock.System.now(),
        startPrice = 10_000_000L,
        floorPrice = 5_000_000L,
        stepDecrement = 500_000L,
        stepIntervalSeconds = 60L
    )

    @BeforeEach
    fun setUp() {
        every {
            jwtTokenProvider.validateToken(token)
        } returns true

        every {
            jwtTokenProvider.getUserIdFromToken(token)
        } returns userId
    }

    @Test
    fun `Dutch create without JWT is rejected`() {
        mockMvc.post("/api/auctions/dutch") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    @Test
    fun `Dutch create with valid authentication reaches validation`() {
        mockMvc.post("/api/auctions/dutch") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `create Dutch auction with valid JWT returns 201`() {
        val sellerId = userId
        val auction = sampleDutchAuction(sellerId = sellerId)

        every {
            dutchAuctionService.createDutchAuction(
                sellerId = sellerId,
                title = "Dutch Test Auction",
                startPrice = 10_000_000L,
                floorPrice = 5_000_000L,
                stepDecrement = 500_000L,
                stepIntervalSeconds = 60L,
                startTime = any<Instant>(),
                endTime = any<Instant>()
            )
        } returns auction

        val startTime = Clock.System.now() + 1.hours
        val endTime = startTime + 2.hours

        mockMvc.post("/api/auctions/dutch") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "title": "Dutch Test Auction",
                    "start_price": 10000000,
                    "floor_price": 5000000,
                    "step_decrement": 500000,
                    "step_interval_seconds": 60,
                    "start_time": "$startTime",
                    "end_time": "$endTime"
                }
                """.trimIndent()

        }.andDo {
            print()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.code") { value(1000) }
            jsonPath("$.result.auction_id") { value(auction.id.toString()) }
            jsonPath("$.result.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `buy Dutch auction with valid JWT returns 200`() {
        val auctionId = Uuid.generateV7()
        val buyerId = userId

        val auction = sampleDutchAuction(
            id = auctionId,
            winnerId = buyerId,
            finalPrice = 8_500_000L,
            status = AuctionStatus.SOLD
        )

        every {
            dutchAuctionService.buyNow(
                auctionId = auctionId,
                buyerId = buyerId,
                now = any<Instant>()
            )
        } returns auction

        mockMvc.post("/api/auctions/$auctionId/buy-dutch") {
            header("Authorization", "Bearer $token")
        }.andDo {
            print()
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value(1000) }
            jsonPath("$.result['auction_id']") { value(auctionId.toString()) }
            jsonPath("$.result['winner_id']") { value(buyerId.toString()) }
            jsonPath("$.result['final_price']") { value(8500000) }
            jsonPath("$.result.status") { value("SOLD") }
        }
    }

    @Test
    fun `buy Dutch auction without JWT is rejected`() {
        val auctionId = Uuid.generateV7()

        mockMvc.post("/api/auctions/$auctionId/buy-dutch")
            .andExpect {
                status { is4xxClientError() }
            }
    }
}
