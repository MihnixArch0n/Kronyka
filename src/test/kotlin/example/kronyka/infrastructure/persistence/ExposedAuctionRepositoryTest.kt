package example.kronyka.infrastructure.persistence

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@SpringBootTest
@Transactional
class ExposedAuctionRepositoryTest {

    @Autowired
    private lateinit var auctionRepository: ExposedAuctionRepository

    private fun insertTestUser(userId: Uuid = Uuid.generateV7()): Uuid {
        UserTable.insert {
            it[id] = userId
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
}
