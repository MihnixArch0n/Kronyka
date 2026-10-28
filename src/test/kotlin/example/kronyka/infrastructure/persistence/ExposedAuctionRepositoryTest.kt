package example.kronyka.infrastructure.persistence

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.infrastructure.persistence.tables.UserTable
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
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
@SpringBootTest
@Transactional
class ExposedAuctionRepositoryTest {

    @Autowired
    private lateinit var auctionRepository: ExposedAuctionRepository

    @Autowired
    private lateinit var userRepository: ExposedUserRepository

    @Test
    fun `listing filters before pagination and counts every matching auction`() {
        val sellerId = insertTestUser()
        val now = Clock.System.now()
        val auctions = (0..2).map { index ->
            createVickreyAuction(
                sellerId = sellerId,
                status = if (index == 0) AuctionStatus.CANCELLED else AuctionStatus.COMMIT
            ).copy(createdAt = now + index.hours).also(auctionRepository::save)
        }

        val page = auctionRepository.findAll(AuctionType.VICKREY, AuctionStatus.COMMIT, 1L, 1)
        page.total shouldBe 2L
        page.items.map { it.id } shouldBe listOf(auctions[1].id)
        auctionRepository.findAll(null, null, 0L, 20).items.map { it.id } shouldBe auctions.reversed().map { it.id }
        auctionRepository.findAll(null, AuctionStatus.CANCELLED, 0L, 20).total shouldBe 1L
        auctionRepository.findAll(AuctionType.DUTCH, null, 0L, 20).total shouldBe 0L
        val beyond = auctionRepository.findAll(null, null, Long.MAX_VALUE, 1)
        beyond.items shouldBe emptyList()
        beyond.total shouldBe 3L
    }

    @Test
    fun `listing uses descending ID to break timestamp ties`() {
        val sellerId = insertTestUser()
        val now = Clock.System.now()
        val auctions = List(3) { createVickreyAuction(sellerId = sellerId).copy(createdAt = now) }
        auctions.forEach(auctionRepository::save)
        val expected = auctions.sortedByDescending { it.id }.map { it.id }

        (0L..2L).map { offset -> auctionRepository.findAll(null, null, offset, 1).items.single().id } shouldBe expected
    }

    @Test
    fun `registered user can be an auction seller`() = runBlocking {
        val user = userRepository.create(Uuid.generateV7().toString(), "test-hash", "Seller")
        val persistedUser = userRepository.findById(user.id).shouldNotBeNull()
        persistedUser.id shouldBe user.id
        persistedUser.createdAt.epochSeconds shouldBe user.createdAt.epochSeconds

        val auction = createVickreyAuction(sellerId = user.id)
        auctionRepository.save(auction)

        auctionRepository.findById(auction.id).shouldNotBeNull().sellerId shouldBe user.id
    }

    private fun insertTestUser(userId: Uuid = Uuid.generateV7()): Uuid {
        UserTable.insert {
            it[id] = userId
            it[username] = userId.toString()
            it[passwordHash] = "test-hash"
            it[fullName] = "Test User"
            it[createdAt] = Clock.System.now()
        }
        return userId
    }

    private fun createVickreyAuction(
        id: Uuid = Uuid.generateV7(),
        sellerId: Uuid,
        title: String = "Vintage Mechanical Keyboard",
        status: AuctionStatus = AuctionStatus.COMMIT,
        reservePrice: Long = 100_000L,
        version: Long = 1L
    ): VickreyAuction {
        val now = Clock.System.now()
        return VickreyAuction(
            id = id,
            sellerId = sellerId,
            title = title,
            type = AuctionType.VICKREY,
            status = status,
            startTime = now,
            endTime = now + 4.hours,
            winnerId = null,
            finalPrice = null,
            version = version,
            createdAt = now,
            reservePrice = reservePrice,
            commitEndTime = now + 2.hours,
            revealEndTime = now + 3.hours,
            secondBidderId = null
        )
    }

    @Test
    @DisplayName("save and findById round-trip for VickreyAuction")
    fun `save and findById round-trip successfully persists and retrieves all VickreyAuction fields`() {
        val sellerId = insertTestUser()
        val auction = createVickreyAuction(sellerId = sellerId)

        auctionRepository.save(auction)

        val retrieved = auctionRepository.findById(auction.id)
        retrieved.shouldNotBeNull()
        (retrieved is VickreyAuction) shouldBe true

        val vickreyRetrieved = retrieved as VickreyAuction
        assertSoftly(vickreyRetrieved) {
            id shouldBe auction.id
            sellerId shouldBe auction.sellerId
            title shouldBe auction.title
            type shouldBe AuctionType.VICKREY
            status shouldBe AuctionStatus.COMMIT
            reservePrice shouldBe auction.reservePrice
            winnerId shouldBe null
            finalPrice shouldBe null
            secondBidderId shouldBe null
            version shouldBe 1L
        }
    }

    @Test
    @DisplayName("findById returns null when auction does not exist")
    fun `findById returns null when auction ID is not found`() {
        val nonExistentId = Uuid.generateV7()
        val result = auctionRepository.findById(nonExistentId)
        result.shouldBeNull()
    }

    @Test
    @DisplayName("save updates existing auction in-place with upsert")
    fun `save updates existing auction status and settlement fields without duplicate rows`() {
        val sellerId = insertTestUser()
        val winnerId = insertTestUser()
        val secondBidderId = insertTestUser()

        val initialAuction = createVickreyAuction(sellerId = sellerId)
        auctionRepository.save(initialAuction)

        val updatedAuction = initialAuction.copy(
            status = AuctionStatus.SETTLED,
            winnerId = winnerId,
            secondBidderId = secondBidderId,
            finalPrice = 280_000L,
            version = 2L
        )
        auctionRepository.save(updatedAuction)

        val retrieved = auctionRepository.findById(initialAuction.id)
        retrieved.shouldNotBeNull()
        val vickreyRetrieved = retrieved as VickreyAuction

        assertSoftly(vickreyRetrieved) {
            id shouldBe initialAuction.id
            status shouldBe AuctionStatus.SETTLED
            winnerId shouldBe winnerId
            secondBidderId shouldBe secondBidderId
            finalPrice shouldBe 280_000L
            version shouldBe 2L
        }
    }

    @Test
    @DisplayName("findExpiredUnsettledVickreyAuctions retrieves only unsettled auctions past revealEndTime")
    fun `findExpiredUnsettledVickreyAuctions filters correctly by revealEndTime and status`() {
        val sellerId = insertTestUser()
        val now = Clock.System.now()

        // Expired unsettled auction 1 (older)
        val expiredAuction1 = createVickreyAuction(sellerId = sellerId).copy(
            commitEndTime = now - 2.hours,
            revealEndTime = now - 1.hours,
            endTime = now - 1.hours,
            status = AuctionStatus.COMMIT
        )
        // Expired unsettled auction 2 (newer)
        val expiredAuction2 = createVickreyAuction(sellerId = sellerId).copy(
            commitEndTime = now - 1.hours,
            revealEndTime = now - 30.minutes,
            endTime = now - 30.minutes,
            status = AuctionStatus.REVEAL
        )
        // Not expired auction
        val futureAuction = createVickreyAuction(sellerId = sellerId).copy(
            commitEndTime = now + 1.hours,
            revealEndTime = now + 2.hours,
            endTime = now + 2.hours,
            status = AuctionStatus.COMMIT
        )
        // Expired but already SETTLED
        val settledAuction = createVickreyAuction(sellerId = sellerId).copy(
            commitEndTime = now - 2.hours,
            revealEndTime = now - 1.hours,
            endTime = now - 1.hours,
            status = AuctionStatus.SETTLED
        )
        // Expired but CANCELLED
        val cancelledAuction = createVickreyAuction(sellerId = sellerId).copy(
            commitEndTime = now - 2.hours,
            revealEndTime = now - 1.hours,
            endTime = now - 1.hours,
            status = AuctionStatus.CANCELLED
        )

        auctionRepository.save(expiredAuction1)
        auctionRepository.save(expiredAuction2)
        auctionRepository.save(futureAuction)
        auctionRepository.save(settledAuction)
        auctionRepository.save(cancelledAuction)

        val results = auctionRepository.findExpiredUnsettledVickreyAuctions(beforeTime = now, limit = 10)
        val resultIds = results.map { it.id }

        resultIds.contains(expiredAuction1.id) shouldBe true
        resultIds.contains(expiredAuction2.id) shouldBe true
        resultIds.contains(futureAuction.id) shouldBe false
        resultIds.contains(settledAuction.id) shouldBe false
        resultIds.contains(cancelledAuction.id) shouldBe false

        // Check order: expiredAuction1 should come before expiredAuction2
        val index1 = resultIds.indexOf(expiredAuction1.id)
        val index2 = resultIds.indexOf(expiredAuction2.id)
        (index1 < index2) shouldBe true

        // Check limit
        val limitedResults = auctionRepository.findExpiredUnsettledVickreyAuctions(beforeTime = now, limit = 1)
        limitedResults.size shouldBe 1
    }

    @Test
    fun `listing filters by dynamic REVEAL and EXPIRED phases`() {
        val sellerId = insertTestUser()
        val now = Clock.System.now()

        // 1. Vickrey auction in REVEAL phase (commitEndTime in past, revealEndTime in future)
        val revealAuction = createVickreyAuction(sellerId = sellerId).copy(
            commitEndTime = now - 30.minutes,
            revealEndTime = now + 30.minutes,
            endTime = now + 1.hours,
            status = AuctionStatus.COMMIT
        ).also(auctionRepository::save)

        // 2. Vickrey auction EXPIRED (revealEndTime in past, unsettled)
        val expiredVickrey = createVickreyAuction(sellerId = sellerId).copy(
            commitEndTime = now - 2.hours,
            revealEndTime = now - 1.hours,
            endTime = now - 1.hours,
            status = AuctionStatus.COMMIT
        ).also(auctionRepository::save)

        // Query REVEAL
        val revealPage = auctionRepository.findAll(AuctionType.VICKREY, AuctionStatus.REVEAL, 0L, 10)
        revealPage.items.map { it.id }.contains(revealAuction.id) shouldBe true
        revealPage.items.map { it.id }.contains(expiredVickrey.id) shouldBe false

        // Query EXPIRED
        val expiredPage = auctionRepository.findAll(null, AuctionStatus.EXPIRED, 0L, 10)
        expiredPage.items.map { it.id }.contains(expiredVickrey.id) shouldBe true
        expiredPage.items.map { it.id }.contains(revealAuction.id) shouldBe false
    }
}
