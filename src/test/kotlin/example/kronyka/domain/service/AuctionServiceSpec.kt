package example.kronyka.domain.service

import example.kronyka.domain.model.*
import example.kronyka.domain.repository.FakeAuctionRepository
import example.kronyka.domain.repository.FakeVickreyBidRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AuctionServiceSpec : BehaviorSpec({

    Given("AuctionService getAuctions") {
        val repository = FakeAuctionRepository()
        val service = AuctionService(repository, VickreyAuctionService(repository, FakeVickreyBidRepository()))
        val now = Clock.System.now()
        val auctions = (0..2).map { index ->
            VickreyAuction(
                id = Uuid.generateV7(), sellerId = Uuid.generateV7(), title = "Auction $index",
                status = if (index == 0) AuctionStatus.CANCELLED else AuctionStatus.COMMIT,
                startTime = now, endTime = now + 3.hours, version = 0L,
                createdAt = now + index.hours, reservePrice = 0L,
                commitEndTime = now + 1.hours, revealEndTime = now + 3.hours
            ).also(repository::save)
        }

        Then("defaults return all auctions newest first") {
            service.getAuctions().items shouldBe auctions.reversed()
            service.getAuctions().total shouldBe 3L
        }
        Then("combined filters preserve the filtered total across pages") {
            val result = service.getAuctions(AuctionType.VICKREY, AuctionStatus.COMMIT, page = 1, size = 1)
            result.items shouldBe listOf(auctions[1])
            result.total shouldBe 2L
        }
        Then("type and status can be filtered independently") {
            service.getAuctions(type = AuctionType.DUTCH).total shouldBe 0L
            service.getAuctions(status = AuctionStatus.CANCELLED).items shouldBe listOf(auctions[0])
        }
        Then("pages beyond the results are empty without losing the total or overflowing") {
            service.getAuctions(page = Int.MAX_VALUE, size = 100).items shouldBe emptyList()
            service.getAuctions(page = Int.MAX_VALUE, size = 100).total shouldBe 3L
        }
        Then("invalid pagination is rejected") {
            shouldThrow<IllegalArgumentException> { service.getAuctions(page = -1) }
            listOf(-1, 0, 101).forEach { size ->
                shouldThrow<IllegalArgumentException> { service.getAuctions(size = size) }
            }
        }
    }

    Given("AuctionService getAuction") {
        val auctionRepo = FakeAuctionRepository()
        val bidRepo = FakeVickreyBidRepository()
        val vickreyService = VickreyAuctionService(auctionRepo, bidRepo)
        val service = AuctionService(auctionRepo, vickreyService)

        When("auction exists in repository") {
            Then("it should return the auction") {
                val now = Clock.System.now()
                val auction = VickreyAuction(
                    id = Uuid.generateV7(),
                    sellerId = Uuid.generateV7(),
                    title = "Vintage Watch",
                    type = AuctionType.VICKREY,
                    status = AuctionStatus.COMMIT,
                    startTime = now - 1.hours,
                    endTime = now + 3.hours,
                    version = 0L,
                    createdAt = now,
                    reservePrice = 100_000L,
                    commitEndTime = now + 1.hours,
                    revealEndTime = now + 2.hours
                )
                auctionRepo.save(auction)

                val retrieved = service.getAuction(auction.id)
                retrieved shouldBe auction
            }
        }

        When("auction does not exist") {
            Then("it should throw AuctionNotFoundException") {
                val nonExistentId = Uuid.generateV7()
                shouldThrow<AuctionNotFoundException> {
                    service.getAuction(nonExistentId)
                }
            }
        }
    }

    Given("AuctionService cancelAuction") {
        val auctionRepo = FakeAuctionRepository()
        val bidRepo = FakeVickreyBidRepository()
        val vickreyService = VickreyAuctionService(auctionRepo, bidRepo)
        val service = AuctionService(auctionRepo, vickreyService)

        fun createSampleVickreyAuction(
            sellerId: Uuid = Uuid.generateV7(),
            status: AuctionStatus = AuctionStatus.COMMIT
        ): VickreyAuction {
            val now = Clock.System.now()
            return VickreyAuction(
                id = Uuid.generateV7(),
                sellerId = sellerId,
                title = "Collectible Card",
                type = AuctionType.VICKREY,
                status = status,
                startTime = now + 1.hours,
                endTime = now + 3.hours,
                version = 0L,
                createdAt = now,
                reservePrice = 50_000L,
                commitEndTime = now + 2.hours,
                revealEndTime = now + 3.hours
            )
        }

        When("auction does not exist") {
            Then("it should throw AuctionNotFoundException") {
                shouldThrow<AuctionNotFoundException> {
                    service.cancelAuction(
                        auctionId = Uuid.generateV7(),
                        userId = Uuid.generateV7()
                    )
                }
            }
        }

        When("requesting user is not the seller") {
            Then("it should throw UnauthorizedAuctionActionException") {
                val sellerId = Uuid.generateV7()
                val nonSellerId = Uuid.generateV7()
                val auction = createSampleVickreyAuction(sellerId = sellerId)
                auctionRepo.save(auction)

                shouldThrow<UnauthorizedAuctionActionException> {
                    service.cancelAuction(
                        auctionId = auction.id,
                        userId = nonSellerId
                    )
                }
            }
        }

        When("auction is already SETTLED") {
            Then("it should throw InvalidAuctionStateException") {
                val sellerId = Uuid.generateV7()
                val auction = createSampleVickreyAuction(sellerId = sellerId, status = AuctionStatus.SETTLED)
                auctionRepo.save(auction)

                val ex = shouldThrow<InvalidAuctionStateException> {
                    service.cancelAuction(
                        auctionId = auction.id,
                        userId = sellerId
                    )
                }
                ex.message shouldBe "Auction canceled or settled"
            }
        }

        When("auction is already CANCELLED") {
            Then("it should throw InvalidAuctionStateException") {
                val sellerId = Uuid.generateV7()
                val auction = createSampleVickreyAuction(sellerId = sellerId, status = AuctionStatus.CANCELLED)
                auctionRepo.save(auction)

                val ex = shouldThrow<InvalidAuctionStateException> {
                    service.cancelAuction(
                        auctionId = auction.id,
                        userId = sellerId
                    )
                }
                ex.message shouldBe "Auction canceled or settled"
            }
        }

        When("Vickrey auction has at least one commitment") {
            Then("it should throw AuctionCancellationException to protect committed bidders") {
                val sellerId = Uuid.generateV7()
                val auction = createSampleVickreyAuction(sellerId = sellerId, status = AuctionStatus.COMMIT)
                auctionRepo.save(auction)

                val now = Clock.System.now()
                bidRepo.upsert(
                    VickreyBid(
                        id = Uuid.generateV7(),
                        auctionId = auction.id,
                        bidderId = Uuid.generateV7(),
                        blindHash = "hash123",
                        lockupDeposit = 100_000L,
                        createdAt = now
                    )
                )

                shouldThrow<AuctionCancellationException> {
                    service.cancelAuction(
                        auctionId = auction.id,
                        userId = sellerId
                    )
                }

                auctionRepo.findById(auction.id)?.status shouldBe AuctionStatus.COMMIT
            }
        }

        When("seller cancels a Vickrey auction with no commitments") {
            Then("it should update auction status to CANCELLED and persist it") {
                val sellerId = Uuid.generateV7()
                val auction = createSampleVickreyAuction(sellerId = sellerId, status = AuctionStatus.COMMIT)
                auctionRepo.save(auction)

                val cancelledAuction = service.cancelAuction(
                    auctionId = auction.id,
                    userId = sellerId
                )

                cancelledAuction.status shouldBe AuctionStatus.CANCELLED
                cancelledAuction.id shouldBe auction.id

                val stored = auctionRepo.findById(auction.id)
                stored.shouldNotBeNull()
                stored.status shouldBe AuctionStatus.CANCELLED
            }
        }
    }
})
