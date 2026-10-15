package example.kronyka.domain.service

import example.kronyka.domain.model.*
import example.kronyka.domain.repository.FakeAuctionRepository
import example.kronyka.domain.repository.FakeVickreyBidRepository
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AuctionServiceSpec : BehaviorSpec({

    Given("AuctionService createVickreyAuction") {
        val auctionRepo = FakeAuctionRepository()
        val bidRepo = FakeVickreyBidRepository()
        val service = AuctionService(auctionRepo, bidRepo)

        When("reservePrice is negative") {
            Then("it should throw InvalidReservePriceException") {
                val now = Clock.System.now()
                shouldThrow<InvalidReservePriceException> {
                    service.createVickreyAuction(
                        sellerId = Uuid.generateV7(),
                        title = "Vintage Watch",
                        startTime = now + 1.hours,
                        reservePrice = -1L,
                        commitEndTime = now + 2.hours,
                        revealEndTime = now + 3.hours
                    )
                }
            }
        }

        When("startTime is in the past") {
            Then("it should throw InvalidAuctionTimeException") {
                val now = Clock.System.now()
                shouldThrow<InvalidAuctionTimeException> {
                    service.createVickreyAuction(
                        sellerId = Uuid.generateV7(),
                        title = "Vintage Watch",
                        startTime = now - 5.minutes,
                        reservePrice = 100_000L,
                        commitEndTime = now + 2.hours,
                        revealEndTime = now + 3.hours
                    )
                }
            }
        }

        When("commitEndTime is before startTime") {
            Then("it should throw InvalidAuctionTimeException") {
                val now = Clock.System.now()
                shouldThrow<InvalidAuctionTimeException> {
                    service.createVickreyAuction(
                        sellerId = Uuid.generateV7(),
                        title = "Vintage Watch",
                        startTime = now + 2.hours,
                        reservePrice = 100_000L,
                        commitEndTime = now + 1.hours,
                        revealEndTime = now + 3.hours
                    )
                }
            }
        }

        When("revealEndTime is before startTime") {
            Then("it should throw InvalidAuctionTimeException") {
                val now = Clock.System.now()
                shouldThrow<InvalidAuctionTimeException> {
                    service.createVickreyAuction(
                        sellerId = Uuid.generateV7(),
                        title = "Vintage Watch",
                        startTime = now + 2.hours,
                        reservePrice = 100_000L,
                        commitEndTime = now + 3.hours,
                        revealEndTime = now + 1.hours
                    )
                }
            }
        }

        When("revealEndTime is before commitEndTime") {
            Then("it should throw InvalidAuctionTimeException") {
                val now = Clock.System.now()
                shouldThrow<InvalidAuctionTimeException> {
                    service.createVickreyAuction(
                        sellerId = Uuid.generateV7(),
                        title = "Vintage Watch",
                        startTime = now + 1.hours,
                        reservePrice = 100_000L,
                        commitEndTime = now + 3.hours,
                        revealEndTime = now + 2.hours
                    )
                }
            }
        }

        When("valid parameters are provided with positive reservePrice") {
            Then("it should persist and return a new VickreyAuction with COMMIT status") {
                val now = Clock.System.now()
                val sellerId = Uuid.generateV7()
                val title = "Rare Mechanical Keyboard"
                val reservePrice = 250_000L
                val startTime = now + 1.hours
                val commitEndTime = now + 2.hours
                val revealEndTime = now + 3.hours

                val auction = service.createVickreyAuction(
                    sellerId = sellerId,
                    title = title,
                    startTime = startTime,
                    reservePrice = reservePrice,
                    commitEndTime = commitEndTime,
                    revealEndTime = revealEndTime
                )

                assertSoftly(auction) {
                    id.shouldNotBeNull()
                    this.sellerId shouldBe sellerId
                    this.title shouldBe title
                    type shouldBe AuctionType.VICKREY
                    status shouldBe AuctionStatus.COMMIT
                    this.startTime shouldBe startTime
                    endTime shouldBe revealEndTime
                    this.reservePrice shouldBe reservePrice
                    this.commitEndTime shouldBe commitEndTime
                    this.revealEndTime shouldBe revealEndTime
                    version shouldBe 0L
                    winnerId shouldBe null
                    finalPrice shouldBe null
                    secondBidderId shouldBe null
                }

                // Verify persisted in repository
                val savedAuction = auctionRepo.findById(auction.id)
                savedAuction shouldBe auction
            }
        }

        When("reservePrice is zero") {
            Then("it should successfully create the auction") {
                val now = Clock.System.now()
                val sellerId = Uuid.generateV7()
                val auction = service.createVickreyAuction(
                    sellerId = sellerId,
                    title = "Free starting auction",
                    startTime = now + 30.minutes,
                    reservePrice = 0L,
                    commitEndTime = now + 1.hours,
                    revealEndTime = now + 2.hours
                )

                auction.reservePrice shouldBe 0L
                auctionRepo.findById(auction.id) shouldBe auction
            }
        }
    }

    Given("AuctionService cancelAuction") {
        val auctionRepo = FakeAuctionRepository()
        val bidRepo = FakeVickreyBidRepository()
        val service = AuctionService(auctionRepo, bidRepo)

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

                // Verify status remains unchanged in repository
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
