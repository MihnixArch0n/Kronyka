package example.kronyka.integration

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.infrastructure.persistence.ExposedAuctionRepository
import example.kronyka.infrastructure.persistence.ExposedVickreyBidRepository
import example.kronyka.infrastructure.persistence.tables.UserTable
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@SpringBootTest
@Transactional
class VickreyAuctionIntegrationTest {

    @Autowired
    private lateinit var auctionRepository: ExposedAuctionRepository

    @Autowired
    private lateinit var vickreyBidRepository: ExposedVickreyBidRepository

    private lateinit var vickreyService: VickreyAuctionService

    @BeforeEach
    fun setUp() {
        vickreyService = VickreyAuctionService(auctionRepository, vickreyBidRepository)
    }

    private fun computeSha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

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
        startTimeDelta: kotlin.time.Duration = (-1).hours,
        commitEndTimeDelta: kotlin.time.Duration = 1.hours,
        revealEndTimeDelta: kotlin.time.Duration = 2.hours,
        endTimeDelta: kotlin.time.Duration = 3.hours
    ): VickreyAuction {
        val now = Clock.System.now()
        val auction = VickreyAuction(
            id = id,
            sellerId = sellerId,
            title = "Exclusive Art Piece",
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

    @Test
    @DisplayName("full Vickrey auction lifecycle happy path")
    fun `full Vickrey auction lifecycle executes commit, recommit, reveal, and settlement with real database`() {
        val reservePrice = 100_000L
        val auction = createAndSaveVickreyAuction(reservePrice = reservePrice)

        val bidder1 = insertTestUser()
        val bidder2 = insertTestUser()
        val bidder3 = insertTestUser()

        val salt1 = "bidder1_secret_salt"
        val salt2First = "bidder2_initial_salt"
        val salt2Updated = "bidder2_updated_salt"
        val salt3 = "bidder3_secret_salt"

        // 1. Commit phase
        vickreyService.commitBid(
            auctionId = auction.id,
            bidderId = bidder1,
            blindHash = computeSha256("250000_$salt1"),
            lockupDeposit = 300_000L
        )

        vickreyService.commitBid(
            auctionId = auction.id,
            bidderId = bidder2,
            blindHash = computeSha256("180000_$salt2First"),
            lockupDeposit = 200_000L
        )

        vickreyService.commitBid(
            auctionId = auction.id,
            bidderId = bidder3,
            blindHash = computeSha256("150000_$salt3"),
            lockupDeposit = 200_000L
        )

        // Bidder 2 increases bid (recommit)
        vickreyService.commitBid(
            auctionId = auction.id,
            bidderId = bidder2,
            blindHash = computeSha256("320000_$salt2Updated"),
            lockupDeposit = 350_000L
        )

        // Verify total commitments in DB is exactly 3 (bidder2 replaced, not duplicated)
        vickreyBidRepository.countCommitmentsByAuctionId(auction.id) shouldBe 3L

        // 2. Transition auction to Reveal phase
        val nowForReveal = Clock.System.now()
        val revealPhaseAuction = auction.copy(
            commitEndTime = nowForReveal - 10.minutes,
            revealEndTime = nowForReveal + 1.hours
        )
        auctionRepository.save(revealPhaseAuction)

        // 3. Reveal phase
        vickreyService.revealBid(
            auctionId = auction.id,
            bidderId = bidder1,
            actualBid = 250_000L,
            secretSalt = salt1
        )

        vickreyService.revealBid(
            auctionId = auction.id,
            bidderId = bidder2,
            actualBid = 320_000L,
            secretSalt = salt2Updated
        )

        vickreyService.revealBid(
            auctionId = auction.id,
            bidderId = bidder3,
            actualBid = 150_000L,
            secretSalt = salt3
        )

        // 4. Transition auction to Settle phase
        val nowForSettle = Clock.System.now()
        val settlePhaseAuction = revealPhaseAuction.copy(
            commitEndTime = nowForSettle - 2.hours,
            revealEndTime = nowForSettle - 10.minutes
        )
        auctionRepository.save(settlePhaseAuction)

        // 5. Settle auction
        val settlement = vickreyService.settleAuction(auction.id)

        // Highest bid is Bidder 2 (320,000); 2nd highest is Bidder 1 (250,000)
        assertSoftly(settlement) {
            auctionId shouldBe auction.id
            winnerId shouldBe bidder2
            finalPrice shouldBe 250_000L
            secondBidderId shouldBe bidder1
        }

        // Verify persisted state in database
        val updatedAuction = auctionRepository.findById(auction.id)
        updatedAuction.shouldNotBeNull()
        (updatedAuction is VickreyAuction) shouldBe true

        val vickreyInDb = updatedAuction as VickreyAuction
        assertSoftly(vickreyInDb) {
            status shouldBe AuctionStatus.SETTLED
            winnerId shouldBe bidder2
            finalPrice shouldBe 250_000L
            secondBidderId shouldBe bidder1
        }
    }

    @Test
    @DisplayName("settlement with single revealed bidder awards reserve price")
    fun `settlement with single revealed bidder awards reserve price in database`() {
        val reservePrice = 120_000L
        val auction = createAndSaveVickreyAuction(reservePrice = reservePrice)

        val bidderId = insertTestUser()
        val salt = "solo_secret_salt"
        val actualBid = 200_000L

        // Commit
        vickreyService.commitBid(
            auctionId = auction.id,
            bidderId = bidderId,
            blindHash = computeSha256("${actualBid}_$salt"),
            lockupDeposit = 250_000L
        )

        // Transition to Reveal phase
        val now = Clock.System.now()
        auctionRepository.save(
            auction.copy(
                commitEndTime = now - 10.minutes,
                revealEndTime = now + 1.hours
            )
        )

        // Reveal
        vickreyService.revealBid(
            auctionId = auction.id,
            bidderId = bidderId,
            actualBid = actualBid,
            secretSalt = salt
        )

        // Transition to Settle phase
        val settleNow = Clock.System.now()
        auctionRepository.save(
            auction.copy(
                commitEndTime = settleNow - 2.hours,
                revealEndTime = settleNow - 10.minutes
            )
        )

        // Settle
        val settlement = vickreyService.settleAuction(auction.id)

        assertSoftly(settlement) {
            auctionId shouldBe auction.id
            winnerId shouldBe bidderId
            finalPrice shouldBe reservePrice
            secondBidderId shouldBe null
        }

        val updatedAuction = auctionRepository.findById(auction.id) as VickreyAuction
        assertSoftly(updatedAuction) {
            status shouldBe AuctionStatus.SETTLED
            winnerId shouldBe bidderId
            finalPrice shouldBe reservePrice
            secondBidderId shouldBe null
        }
    }

    @Test
    @DisplayName("settlement with zero revealed bids settles unsold")
    fun `settlement with zero revealed bids settles unsold in database`() {
        val auction = createAndSaveVickreyAuction(reservePrice = 100_000L)

        val bidderId = insertTestUser()

        // Bidder commits but never reveals
        vickreyService.commitBid(
            auctionId = auction.id,
            bidderId = bidderId,
            blindHash = computeSha256("150000_unrevealed_salt"),
            lockupDeposit = 200_000L
        )

        // Transition past revealEndTime directly to settle phase
        val now = Clock.System.now()
        auctionRepository.save(
            auction.copy(
                commitEndTime = now - 2.hours,
                revealEndTime = now - 10.minutes
            )
        )

        // Settle
        val settlement = vickreyService.settleAuction(auction.id)

        assertSoftly(settlement) {
            auctionId shouldBe auction.id
            winnerId.shouldBeNull()
            finalPrice.shouldBeNull()
            secondBidderId.shouldBeNull()
        }

        val updatedAuction = auctionRepository.findById(auction.id) as VickreyAuction
        assertSoftly(updatedAuction) {
            status shouldBe AuctionStatus.SETTLED
            winnerId.shouldBeNull()
            finalPrice.shouldBeNull()
            secondBidderId.shouldBeNull()
        }
    }
}
