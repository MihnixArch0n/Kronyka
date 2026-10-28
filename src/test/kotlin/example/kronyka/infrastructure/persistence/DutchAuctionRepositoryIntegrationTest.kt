
package example.kronyka.infrastructure.persistence

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.DutchAuction
import example.kronyka.infrastructure.persistence.tables.UserTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

@SpringBootTest
class DutchAuctionRepositoryIntegrationTest {

    @Autowired
    private lateinit var repository: ExposedAuctionRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private fun createAuction(): DutchAuction {
        val sellerId = Uuid.random()
        val buyerId = Uuid.random()
        val now = Clock.System.now()

        transactionTemplate.executeWithoutResult {
            UserTable.insert {
                it[id] = sellerId
            }
            UserTable.insert {
                it[id] = buyerId
            }
        }

        val auction = DutchAuction(
            id = Uuid.random(),
            sellerId = sellerId,
            title = "Dutch Concurrency Integration Test",
            type = AuctionType.DUTCH,
            status = AuctionStatus.ACTIVE,
            startTime = now - 1.hours,
            endTime = now + 1.hours,
            winnerId = null,
            finalPrice = null,
            version = 0L,
            createdAt = now,
            startPrice = 1_000_000L,
            floorPrice = 500_000L,
            stepDecrement = 50_000L,
            stepIntervalSeconds = 60L
        )

        repository.save(auction)
        return auction
    }

    private fun createUser(): Uuid {
        val userId = Uuid.random()

        transactionTemplate.executeWithoutResult {
            UserTable.insert {
                it[id] = userId
            }
        }

        return userId
    }

    @Test
    fun `two concurrent buyers only one succeeds`() {
        val auction = createAuction()
        val buyerA = createUser()
        val buyerB = createUser()

        val results = runConcurrently(
            {
                repository.tryCompleteDutchPurchase(
                    auction.id, buyerA, 0L,
                    750_000L, Clock.System.now()
                )
            },
            {
                repository.tryCompleteDutchPurchase(
                    auction.id, buyerB, 0L,
                    750_000L, Clock.System.now()
                )
            }
        )

        assertEquals(1, results.count { it })

        val saved = repository.findById(auction.id) as DutchAuction
        assertEquals(AuctionStatus.SOLD, saved.status)
        assertTrue(saved.winnerId == buyerA || saved.winnerId == buyerB)
        assertEquals(750_000L, saved.finalPrice)
        assertEquals(1L, saved.version)
    }

    @Test
    fun `concurrent buy and cancel only one succeeds`() {
        val auction = createAuction()
        val buyerId = createUser()

        val results = runConcurrently(
            {
                repository.tryCompleteDutchPurchase(
                    auction.id, buyerId, 0L,
                    750_000L, Clock.System.now()
                )
            },
            {
                repository.tryCancelDutchAuction(
                    auction.id, auction.sellerId, 0L
                )
            }
        )

        assertEquals(1, results.count { it })

        val saved = repository.findById(auction.id) as DutchAuction
        assertEquals(1L, saved.version)

        if (results[0]) {
            assertEquals(AuctionStatus.SOLD, saved.status)
            assertEquals(buyerId, saved.winnerId)
            assertEquals(750_000L, saved.finalPrice)
        } else {
            assertEquals(AuctionStatus.CANCELLED, saved.status)
            assertNull(saved.winnerId)
            assertNull(saved.finalPrice)
        }
    }

    @Test
    fun `stale version update is rejected`() {
        val auction = createAuction()
        val buyerId = createUser()

        val success = repository.tryCompleteDutchPurchase(
            auction.id, buyerId, 99L,
            750_000L, Clock.System.now()
        )

        assertFalse(success)

        val saved = repository.findById(auction.id) as DutchAuction
        assertEquals(AuctionStatus.ACTIVE, saved.status)
        assertEquals(0L, saved.version)
        assertNull(saved.winnerId)
    }

    private fun runConcurrently(
        first: () -> Boolean,
        second: () -> Boolean
    ): List<Boolean> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        try {
            val tasks = listOf(first, second).map { action ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS)) {
                        "Timed out waiting for start"
                    }
                    action()
                })
            }

            check(ready.await(10, TimeUnit.SECONDS)) {
                "Workers did not become ready"
            }

            start.countDown()

            return tasks.map {
                it.get(15, TimeUnit.SECONDS)
            }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
