package example.kronyka.infrastructure.persistence

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.model.VickreyBid
import example.kronyka.infrastructure.persistence.tables.UserTable
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
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
@SpringBootTest
@Transactional
class ExposedVickreyBidRepositoryTest {

    @Autowired
    private lateinit var vickreyBidRepository: ExposedVickreyBidRepository

    @Autowired
    private lateinit var auctionRepository: ExposedAuctionRepository

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

    private fun createAndSaveAuction(
        id: Uuid = Uuid.generateV7(),
        sellerId: Uuid = insertTestUser(),
        reservePrice: Long = 100_000L
    ): VickreyAuction {
        val now = Clock.System.now()
        val auction = VickreyAuction(
            id = id,
            sellerId = sellerId,
            title = "Limited Edition Item",
            type = AuctionType.VICKREY,
            status = AuctionStatus.COMMIT,
            startTime = now,
            endTime = now + 4.hours,
            winnerId = null,
            finalPrice = null,
            version = 1L,
            createdAt = now,
            reservePrice = reservePrice,
            commitEndTime = now + 2.hours,
            revealEndTime = now + 3.hours,
            secondBidderId = null
        )
        auctionRepository.save(auction)
        return auction
    }

    @Test
    @DisplayName("upsert saves initial commitment and findByAuctionIdAndBidderId retrieves it")
    fun `upsert saves initial commitment and findByAuctionIdAndBidderId retrieves it`() {
        val auction = createAndSaveAuction()
        val bidderId = insertTestUser()
        val now = Clock.System.now()

        val bid = VickreyBid(
            id = Uuid.generateV7(),
            auctionId = auction.id,
            bidderId = bidderId,
            blindHash = "hash_abc_123",
            lockupDeposit = 200_000L,
            actualBid = null,
            isRevealed = false,
            createdAt = now,
            revealedAt = null
        )

        vickreyBidRepository.upsert(bid)

        val retrieved = vickreyBidRepository.findByAuctionIdAndBidderId(auction.id, bidderId)
        retrieved.shouldNotBeNull()

        assertSoftly(retrieved) {
            id shouldBe bid.id
            auctionId shouldBe auction.id
            this.bidderId shouldBe bidderId
            blindHash shouldBe "hash_abc_123"
            lockupDeposit shouldBe 200_000L
            actualBid shouldBe null
            isRevealed shouldBe false
            revealedAt shouldBe null
        }
    }

    @Test
    @DisplayName("findByAuctionIdAndBidderId returns null when bid does not exist")
    fun `findByAuctionIdAndBidderId returns null when no bid exists`() {
        val auction = createAndSaveAuction()
        val bidderId = insertTestUser()

        val retrieved = vickreyBidRepository.findByAuctionIdAndBidderId(auction.id, bidderId)
        retrieved.shouldBeNull()
    }

    @Test
    @DisplayName("upsert performs atomic update on recommitment with same auction_id and bidder_id")
    fun `upsert performs atomic update on recommitment without duplicate records`() {
        val auction = createAndSaveAuction()
        val bidderId = insertTestUser()
        val now = Clock.System.now()

        val firstCommit = VickreyBid(
            id = Uuid.generateV7(),
            auctionId = auction.id,
            bidderId = bidderId,
            blindHash = "hash_commit_1",
            lockupDeposit = 150_000L,
            actualBid = null,
            isRevealed = false,
            createdAt = now,
            revealedAt = null
        )
        vickreyBidRepository.upsert(firstCommit)

        val secondCommit = firstCommit.copy(
            blindHash = "hash_commit_2_updated",
            lockupDeposit = 300_000L
        )
        vickreyBidRepository.upsert(secondCommit)

        val retrieved = vickreyBidRepository.findByAuctionIdAndBidderId(auction.id, bidderId)
        retrieved.shouldNotBeNull()

        assertSoftly(retrieved) {
            id shouldBe firstCommit.id
            blindHash shouldBe "hash_commit_2_updated"
            lockupDeposit shouldBe 300_000L
            isRevealed shouldBe false
        }

        vickreyBidRepository.countCommitmentsByAuctionId(auction.id) shouldBe 1L
    }

    @Test
    @DisplayName("upsert preserves original id and createdAt on recommitment with different id")
    fun `upsert preserves original id and createdAt on recommitment with different id`() {
        val auction = createAndSaveAuction()
        val bidderId = insertTestUser()
        val initialTime = Clock.System.now() - 1.hours
        val originalId = Uuid.generateV7()

        val firstCommit = VickreyBid(
            id = originalId,
            auctionId = auction.id,
            bidderId = bidderId,
            blindHash = "hash_commit_1",
            lockupDeposit = 150_000L,
            actualBid = null,
            isRevealed = false,
            createdAt = initialTime,
            revealedAt = null
        )
        vickreyBidRepository.upsert(firstCommit)

        val newGeneratedId = Uuid.generateV7()
        val laterTime = Clock.System.now()
        val secondCommit = VickreyBid(
            id = newGeneratedId,
            auctionId = auction.id,
            bidderId = bidderId,
            blindHash = "hash_commit_2_updated",
            lockupDeposit = 300_000L,
            actualBid = null,
            isRevealed = false,
            createdAt = laterTime,
            revealedAt = null
        )
        vickreyBidRepository.upsert(secondCommit)

        val retrieved = vickreyBidRepository.findByAuctionIdAndBidderId(auction.id, bidderId)
        retrieved.shouldNotBeNull()

        assertSoftly(retrieved) {
            id shouldBe originalId
            createdAt shouldBe initialTime
            blindHash shouldBe "hash_commit_2_updated"
            lockupDeposit shouldBe 300_000L
        }
    }

    @Test
    @DisplayName("upsert updates revealed bid")
    fun `upsert updates revealed bid with actualBid, isRevealed true, and revealedAt`() {
        val auction = createAndSaveAuction()
        val bidderId = insertTestUser()
        val commitTime = Clock.System.now()

        val initialCommit = VickreyBid(
            id = Uuid.generateV7(),
            auctionId = auction.id,
            bidderId = bidderId,
            blindHash = "hash_for_reveal_test",
            lockupDeposit = 250_000L,
            actualBid = null,
            isRevealed = false,
            createdAt = commitTime,
            revealedAt = null
        )
        vickreyBidRepository.upsert(initialCommit)

        val revealTime = commitTime + 30.minutes
        val revealedBid = initialCommit.copy(
            actualBid = 220_000L,
            isRevealed = true,
            revealedAt = revealTime
        )
        vickreyBidRepository.upsert(revealedBid)

        val retrieved = vickreyBidRepository.findByAuctionIdAndBidderId(auction.id, bidderId)
        retrieved.shouldNotBeNull()

        assertSoftly(retrieved) {
            id shouldBe initialCommit.id
            actualBid shouldBe 220_000L
            isRevealed shouldBe true
            revealedAt.shouldNotBeNull()
        }
    }

    @Test
    @DisplayName("countCommitmentsByAuctionId accurately counts total commitments across multiple bidders")
    fun `countCommitmentsByAuctionId accurately counts commitments per auction`() {
        val auctionA = createAndSaveAuction()
        val auctionB = createAndSaveAuction()

        val bidder1 = insertTestUser()
        val bidder2 = insertTestUser()
        val bidder3 = insertTestUser()

        val now = Clock.System.now()

        // 3 commitments for Auction A
        vickreyBidRepository.upsert(
            VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auctionA.id,
                bidderId = bidder1,
                blindHash = "hash_a1",
                lockupDeposit = 150_000L,
                createdAt = now
            )
        )
        vickreyBidRepository.upsert(
            VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auctionA.id,
                bidderId = bidder2,
                blindHash = "hash_a2",
                lockupDeposit = 180_000L,
                createdAt = now
            )
        )
        vickreyBidRepository.upsert(
            VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auctionA.id,
                bidderId = bidder3,
                blindHash = "hash_a3",
                lockupDeposit = 200_000L,
                createdAt = now
            )
        )

        // 1 commitment for Auction B
        vickreyBidRepository.upsert(
            VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auctionB.id,
                bidderId = bidder1,
                blindHash = "hash_b1",
                lockupDeposit = 250_000L,
                createdAt = now
            )
        )

        vickreyBidRepository.countCommitmentsByAuctionId(auctionA.id) shouldBe 3L
        vickreyBidRepository.countCommitmentsByAuctionId(auctionB.id) shouldBe 1L
    }

    @Test
    @DisplayName("findAllRevealedBidsByAuctionId returns only revealed bids for specific auction")
    fun `findAllRevealedBidsByAuctionId returns only revealed bids for the given auction`() {
        val auctionA = createAndSaveAuction()
        val auctionB = createAndSaveAuction()

        val bidder1 = insertTestUser()
        val bidder2 = insertTestUser()
        val bidder3 = insertTestUser()

        val now = Clock.System.now()

        // Auction A: Bidder 1 revealed
        vickreyBidRepository.upsert(
            VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auctionA.id,
                bidderId = bidder1,
                blindHash = "hash1",
                lockupDeposit = 200_000L,
                actualBid = 180_000L,
                isRevealed = true,
                createdAt = now,
                revealedAt = now + 10.minutes
            )
        )

        // Auction A: Bidder 2 unrevealed
        vickreyBidRepository.upsert(
            VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auctionA.id,
                bidderId = bidder2,
                blindHash = "hash2",
                lockupDeposit = 250_000L,
                actualBid = null,
                isRevealed = false,
                createdAt = now,
                revealedAt = null
            )
        )

        // Auction A: Bidder 3 revealed
        vickreyBidRepository.upsert(
            VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auctionA.id,
                bidderId = bidder3,
                blindHash = "hash3",
                lockupDeposit = 300_000L,
                actualBid = 290_000L,
                isRevealed = true,
                createdAt = now,
                revealedAt = now + 10.minutes
            )
        )

        // Auction B: Bidder 1 revealed (should be excluded when querying Auction A)
        vickreyBidRepository.upsert(
            VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auctionB.id,
                bidderId = bidder1,
                blindHash = "hash_b",
                lockupDeposit = 150_000L,
                actualBid = 140_000L,
                isRevealed = true,
                createdAt = now,
                revealedAt = now + 10.minutes
            )
        )

        val revealedBidsA = vickreyBidRepository.findAllRevealedBidsByAuctionId(auctionA.id)
        revealedBidsA shouldHaveSize 2

        val bidderIds = revealedBidsA.map { it.bidderId }
        bidderIds.contains(bidder1) shouldBe true
        bidderIds.contains(bidder3) shouldBe true
        bidderIds.contains(bidder2) shouldBe false

        revealedBidsA.all { it.isRevealed } shouldBe true
        revealedBidsA.all { it.auctionId == auctionA.id } shouldBe true
    }
}
