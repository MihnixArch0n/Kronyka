package example.kronyka.domain.service

import example.kronyka.domain.model.DutchPurchaseConflictException
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.DutchAuction
import example.kronyka.domain.model.InvalidAuctionStateException
import example.kronyka.domain.repository.FakeAuctionRepository
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DutchAuctionServiceTest {

    private lateinit var repository: FakeAuctionRepository
    private lateinit var service: DutchAuctionService

    private val sellerId = Uuid.random()
    private val buyerA = Uuid.random()
    private val buyerB = Uuid.random()

    private val startTime =
        Instant.parse("2026-10-27T10:00:00Z")

    @BeforeTest
    fun setup() {
        repository = FakeAuctionRepository()
        service = DutchAuctionService(
            auctionRepository = repository,
            priceCalculator = DutchPriceCalculator()
        )
    }

    private fun createAuction(): DutchAuction {
        return service.createDutchAuction(
            sellerId = sellerId,
            title = "Dutch Auction Test",
            startPrice = 10_000_000L,
            floorPrice = 5_000_000L,
            stepDecrement = 500_000L,
            stepIntervalSeconds = 60L,
            startTime = startTime,
            endTime = startTime + 2.hours
        )
    }

    @Test
    fun `create auction saves correct initial data`() {
        val auction = createAuction()

        assertEquals(AuctionStatus.ACTIVE, auction.status)
        assertEquals(0L, auction.version)
        assertNull(auction.winnerId)
        assertNull(auction.finalPrice)

        val saved = repository.findById(auction.id)
        assertNotNull(saved)
        assertEquals(auction.id, saved.id)
    }

    @Test
    fun `get current price decreases correctly`() {
        val auction = createAuction()

        val price = service.getCurrentPrice(
            auctionId = auction.id,
            now = startTime + kotlin.time.Duration.parse("180s")
        )

        assertEquals(8_500_000L, price)
    }

    @Test
    fun `buy now saves correct final price`() {
        val auction = createAuction()

        val result = service.buyNow(
            auctionId = auction.id,
            buyerId = buyerA,
            now = startTime + kotlin.time.Duration.parse("180s")
        )

        assertEquals(8_500_000L, result.finalPrice)
        assertEquals(AuctionStatus.SOLD, result.status)
        assertEquals(buyerA, result.winnerId)
        assertEquals(1L, result.version)

        val saved = repository.findById(auction.id) as DutchAuction
        assertEquals(8_500_000L, saved.finalPrice)
    }

    @Test
    fun `buy at exact step boundary uses new price`() {
        val auction = createAuction()

        val result = service.buyNow(
            auctionId = auction.id,
            buyerId = buyerA,
            now = startTime + kotlin.time.Duration.parse("60s")
        )

        assertEquals(9_500_000L, result.finalPrice)
        assertEquals(AuctionStatus.SOLD, result.status)
    }

    @Test
    fun `second buyer cannot purchase sold auction`() {
        val auction = createAuction()
        val now = startTime + kotlin.time.Duration.parse("120s")

        service.buyNow(auction.id, buyerA, now)

        assertFailsWith<DutchPurchaseConflictException> {
            service.buyNow(auction.id, buyerB, now)
        }

        val saved = repository.findById(auction.id) as DutchAuction

        assertEquals(buyerA, saved.winnerId)
        assertEquals(9_000_000L, saved.finalPrice)
        assertEquals(1L, saved.version)
    }

    @Test
    fun `seller cannot buy own auction`() {
        val auction = createAuction()

        assertFailsWith<InvalidAuctionStateException> {
            service.buyNow(
                auction.id,
                sellerId,
                startTime + kotlin.time.Duration.parse("60s")
            )
        }
    }

    @Test
    fun `cannot buy before auction starts`() {
        val auction = createAuction()

        assertFailsWith<InvalidAuctionStateException> {
            service.buyNow(
                auction.id,
                buyerA,
                startTime - kotlin.time.Duration.parse("1s")
            )
        }
    }

    @Test
    fun `cannot buy after auction expires`() {
        val auction = createAuction()

        assertFailsWith<InvalidAuctionStateException> {
            service.buyNow(
                auction.id,
                buyerA,
                startTime + 3.hours
            )
        }
    }

    @Test
    fun `cannot buy cancelled auction`() {
        val auction = createAuction()

        repository.tryCancelDutchAuction(
            auctionId = auction.id,
            sellerId = sellerId,
            expectedVersion = 0L
        )

        assertFailsWith<InvalidAuctionStateException> {
            service.buyNow(
                auction.id,
                buyerA,
                startTime + kotlin.time.Duration.parse("60s")
            )
        }
    }
}
