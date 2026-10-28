
package example.kronyka.domain.service

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.DutchAuction
import example.kronyka.domain.repository.FakeAuctionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DutchAuctionConcurrencyTest {

    private lateinit var repository: FakeAuctionRepository

    private val sellerId = Uuid.random()
    private val buyerA = Uuid.random()
    private val buyerB = Uuid.random()

    private val startTime =
        Instant.parse("2026-10-27T10:00:00Z")

    private val now =
        Instant.parse("2026-10-27T10:05:00Z")

    @BeforeEach
    fun setup() {
        repository = FakeAuctionRepository()
    }

    private fun createAuction(): DutchAuction {
        val auction = DutchAuction(
            id = Uuid.random(),
            sellerId = sellerId,
            title = "Dutch Auction Test",
            type = AuctionType.DUTCH,
            status = AuctionStatus.ACTIVE,
            startTime = startTime,
            endTime = startTime + 2.hours,
            winnerId = null,
            finalPrice = null,
            version = 0L,
            createdAt = startTime,
            startPrice = 1_000_000L,
            floorPrice = 500_000L,
            stepDecrement = 50_000L,
            stepIntervalSeconds = 60L
        )

        repository.save(auction)
        return auction
    }

    @Test
    fun `buy first then cancel must fail`() {
        val auction = createAuction()

        val bought = repository.tryCompleteDutchPurchase(
            auctionId = auction.id,
            buyerId = buyerA,
            expectedVersion = 0L,
            finalPrice = 750_000L,
            now = now
        )

        val cancelled = repository.tryCancelDutchAuction(
            auctionId = auction.id,
            sellerId = sellerId,
            expectedVersion = 0L
        )

        assertTrue(bought)
        assertFalse(cancelled)

        val result = repository.findById(auction.id) as DutchAuction

        assertEquals(AuctionStatus.SOLD, result.status)
        assertEquals(buyerA, result.winnerId)
        assertEquals(1L, result.version)
    }

    @Test
    fun `cancel first then buy must fail`() {
        val auction = createAuction()

        val cancelled = repository.tryCancelDutchAuction(
            auctionId = auction.id,
            sellerId = sellerId,
            expectedVersion = 0L
        )

        val bought = repository.tryCompleteDutchPurchase(
            auctionId = auction.id,
            buyerId = buyerA,
            expectedVersion = 0L,
            finalPrice = 750_000L,
            now = now
        )

        assertTrue(cancelled)
        assertFalse(bought)

        val result = repository.findById(auction.id) as DutchAuction

        assertEquals(AuctionStatus.CANCELLED, result.status)
        assertEquals(null, result.winnerId)
        assertEquals(1L, result.version)
    }

    @Test
    fun `two buyers cannot both win`() {
        val auction = createAuction()

        val first = repository.tryCompleteDutchPurchase(
            auctionId = auction.id,
            buyerId = buyerA,
            expectedVersion = 0L,
            finalPrice = 750_000L,
            now = now
        )

        val second = repository.tryCompleteDutchPurchase(
            auctionId = auction.id,
            buyerId = buyerB,
            expectedVersion = 0L,
            finalPrice = 750_000L,
            now = now
        )

        assertTrue(first)
        assertFalse(second)

        val result = repository.findById(auction.id) as DutchAuction

        assertEquals(buyerA, result.winnerId)
        assertEquals(AuctionStatus.SOLD, result.status)
        assertEquals(1L, result.version)
    }

    @Test
    fun `stale version must be rejected`() {
        val auction = createAuction()

        val cancelled = repository.tryCancelDutchAuction(
            auctionId = auction.id,
            sellerId = sellerId,
            expectedVersion = 99L
        )

        assertFalse(cancelled)

        val result = repository.findById(auction.id) as DutchAuction

        assertEquals(AuctionStatus.ACTIVE, result.status)
        assertEquals(0L, result.version)
    }
}
