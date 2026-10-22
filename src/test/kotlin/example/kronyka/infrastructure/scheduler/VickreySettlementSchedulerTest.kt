package example.kronyka.infrastructure.scheduler

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.model.VickreyBid
import example.kronyka.infrastructure.persistence.ExposedAuctionRepository
import example.kronyka.infrastructure.persistence.ExposedVickreyBidRepository
import example.kronyka.infrastructure.persistence.tables.UserTable
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@SpringBootTest(properties = ["kronyka.auction.settlement.enabled=true"])
@Transactional
class VickreySettlementSchedulerTest {

    @Autowired
    private lateinit var scheduler: VickreySettlementScheduler

    @Autowired
    private lateinit var auctionRepository: ExposedAuctionRepository

    @Autowired
    private lateinit var bidRepository: ExposedVickreyBidRepository

    private fun insertTestUser(userId: Uuid = Uuid.generateV7()): Uuid {
        UserTable.insert {
            it[id] = userId
        }
        return userId
    }

    private fun createAndSaveVickreyAuction(
        id: Uuid = Uuid.generateV7(),
        sellerId: Uuid = insertTestUser(),
        reservePrice: Long = 100_000L,
        startTimeDelta: kotlin.time.Duration = (-3).hours,
        commitEndTimeDelta: kotlin.time.Duration = (-2).hours,
        revealEndTimeDelta: kotlin.time.Duration = (-1).hours,
        endTimeDelta: kotlin.time.Duration = (-1).hours
    ): VickreyAuction {
        val now = Clock.System.now()
        val auction = VickreyAuction(
            id = id,
            sellerId = sellerId,
            title = "Scheduled Test Item",
            type = AuctionType.VICKREY,
            status = AuctionStatus.COMMIT,
            startTime = now + startTimeDelta,
            endTime = now + endTimeDelta,
            winnerId = null,
            finalPrice = null,
            version = 1L,
            createdAt = now + startTimeDelta,
            reservePrice = reservePrice,
            commitEndTime = now + commitEndTimeDelta,
            revealEndTime = now + revealEndTimeDelta,
            secondBidderId = null
        )
        auctionRepository.save(auction)
        return auction
    }

    private fun createAndSaveRevealedBid(
        auctionId: Uuid,
        bidderId: Uuid,
        actualBid: Long,
        lockupDeposit: Long = actualBid + 10_000L
    ): VickreyBid {
        val now = Clock.System.now()
        val bid = VickreyBid(
            id = Uuid.generateV7(),
            auctionId = auctionId,
            bidderId = bidderId,
            blindHash = "dummyHash",
            lockupDeposit = lockupDeposit,
            actualBid = actualBid,
            isRevealed = true,
            createdAt = now - 2.hours,
            revealedAt = now - 1.hours
        )
        bidRepository.upsert(bid)
        return bid
    }

    @Test
    @DisplayName("settleExpiredAuctions settles expired auctions with second-price rule and leaves future auctions untouched")
    fun `settleExpiredAuctions settles expired auctions and ignores unexpired auctions`() {
        val sellerId = insertTestUser()
        val bidder1 = insertTestUser()
        val bidder2 = insertTestUser()
        val bidder3 = insertTestUser()

        // 1. Expired auction with 2 revealed bids
        val expiredAuction = createAndSaveVickreyAuction(
            sellerId = sellerId,
            reservePrice = 100_000L
        )
        createAndSaveRevealedBid(expiredAuction.id, bidder1, 300_000L)
        createAndSaveRevealedBid(expiredAuction.id, bidder2, 200_000L)

        // 2. Future auction (not yet expired)
        val futureAuction = createAndSaveVickreyAuction(
            sellerId = sellerId,
            reservePrice = 150_000L,
            startTimeDelta = (-1).hours,
            commitEndTimeDelta = 1.hours,
            revealEndTimeDelta = 2.hours,
            endTimeDelta = 2.hours
        )
        createAndSaveRevealedBid(futureAuction.id, bidder3, 500_000L)

        // Act: Run scheduler
        scheduler.settleExpiredAuctions()

        // Assert: Expired auction must be settled with 2nd price
        val retrievedExpired = auctionRepository.findById(expiredAuction.id) as? VickreyAuction
        retrievedExpired.shouldNotBeNull()
        assertSoftly(retrievedExpired) {
            status shouldBe AuctionStatus.SETTLED
            winnerId shouldBe bidder1
            finalPrice shouldBe 200_000L
            secondBidderId shouldBe bidder2
        }

        // Assert: Future auction must remain unsettled
        val retrievedFuture = auctionRepository.findById(futureAuction.id) as? VickreyAuction
        retrievedFuture.shouldNotBeNull()
        assertSoftly(retrievedFuture) {
            status shouldBe AuctionStatus.COMMIT
            winnerId.shouldBeNull()
            finalPrice.shouldBeNull()
        }
    }

    @Test
    @DisplayName("settleExpiredAuctions marks auction settled with no winner when 0 revealed bids exist")
    fun `settleExpiredAuctions settles auction with no winner when no bids are revealed`() {
        val expiredAuction = createAndSaveVickreyAuction(reservePrice = 100_000L)

        scheduler.settleExpiredAuctions()

        val retrieved = auctionRepository.findById(expiredAuction.id) as? VickreyAuction
        retrieved.shouldNotBeNull()
        assertSoftly(retrieved) {
            status shouldBe AuctionStatus.SETTLED
            winnerId.shouldBeNull()
            finalPrice.shouldBeNull()
            secondBidderId.shouldBeNull()
        }
    }

    @Test
    @DisplayName("settleExpiredAuctions marks single bidder as winner at reserve price")
    fun `settleExpiredAuctions settles single bid at reserve price`() {
        val bidder = insertTestUser()
        val expiredAuction = createAndSaveVickreyAuction(reservePrice = 80_000L)
        createAndSaveRevealedBid(expiredAuction.id, bidder, 150_000L)

        scheduler.settleExpiredAuctions()

        val retrieved = auctionRepository.findById(expiredAuction.id) as? VickreyAuction
        retrieved.shouldNotBeNull()
        assertSoftly(retrieved) {
            status shouldBe AuctionStatus.SETTLED
            winnerId shouldBe bidder
            finalPrice shouldBe 80_000L
            secondBidderId.shouldBeNull()
        }
    }
}
