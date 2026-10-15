package example.kronyka.domain.service

import example.kronyka.domain.model.*
import example.kronyka.domain.repository.FakeAuctionRepository
import example.kronyka.domain.repository.FakeVickreyBidRepository
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class VickreyAuctionServiceSpec : BehaviorSpec({

    fun computeSha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun createVickreyAuction(
        id: Uuid = Uuid.generateV7(),
        sellerId: Uuid = Uuid.generateV7(),
        title: String = "Rare Mechanical Keyboard",
        status: AuctionStatus = AuctionStatus.COMMIT,
        reservePrice: Long = 100_000L,
        startTime: Instant,
        commitEndTime: Instant,
        revealEndTime: Instant,
        endTime: Instant
    ): VickreyAuction = VickreyAuction(
        id = id,
        sellerId = sellerId,
        title = title,
        type = AuctionType.VICKREY,
        status = status,
        startTime = startTime,
        endTime = endTime,
        version = 1L,
        createdAt = startTime,
        reservePrice = reservePrice,
        commitEndTime = commitEndTime,
        revealEndTime = revealEndTime
    )

    data class DummyNonVickreyAuction(
        override val id: Uuid = Uuid.generateV7(),
        override val sellerId: Uuid = Uuid.generateV7(),
        override val title: String = "Non-Vickrey Item",
        override val type: AuctionType = AuctionType.DUTCH,
        override val status: AuctionStatus = AuctionStatus.ACTIVE,
        override val startTime: Instant,
        override val endTime: Instant,
        override val winnerId: Uuid? = null,
        override val finalPrice: Long? = null,
        override val version: Long = 1L,
        override val createdAt: Instant = startTime
    ) : Auction

    Given("VickreyAuctionService commitBid phase") {
        val auctionRepo = FakeAuctionRepository()
        val bidRepo = FakeVickreyBidRepository()
        val service = VickreyAuctionService(auctionRepo, bidRepo)

        When("auction does not exist") {
            Then("it should throw AuctionNotFoundException") {
                val nonExistentAuctionId = Uuid.generateV7()
                val bidderId = Uuid.generateV7()

                shouldThrow<AuctionNotFoundException> {
                    service.commitBid(
                        auctionId = nonExistentAuctionId,
                        bidderId = bidderId,
                        blindHash = "some_hash",
                        lockupDeposit = 200_000L
                    )
                }
            }
        }

        When("auction is not a Vickrey auction") {
            Then("it should throw InvalidAuctionTypeException") {
                val now = Clock.System.now()
                val nonVickreyAuction = DummyNonVickreyAuction(
                    startTime = now - 1.hours,
                    endTime = now + 1.hours
                )
                auctionRepo.save(nonVickreyAuction)

                shouldThrow<InvalidAuctionTypeException> {
                    service.commitBid(
                        auctionId = nonVickreyAuction.id,
                        bidderId = Uuid.generateV7(),
                        blindHash = "some_hash",
                        lockupDeposit = 200_000L
                    )
                }
            }
        }

        When("current time is before auction startTime") {
            Then("it should throw InvalidAuctionStateException") {
                val now = Clock.System.now()
                val futureAuction = createVickreyAuction(
                    startTime = now + 10.minutes,
                    commitEndTime = now + 30.minutes,
                    revealEndTime = now + 1.hours,
                    endTime = now + 2.hours
                )
                auctionRepo.save(futureAuction)

                val exception = shouldThrow<InvalidAuctionStateException> {
                    service.commitBid(
                        auctionId = futureAuction.id,
                        bidderId = Uuid.generateV7(),
                        blindHash = "some_hash",
                        lockupDeposit = 200_000L
                    )
                }
                exception.message shouldBe "Not in commit phase"
            }
        }

        When("current time is past commitEndTime") {
            Then("it should throw InvalidAuctionStateException") {
                val now = Clock.System.now()
                val pastCommitAuction = createVickreyAuction(
                    startTime = now - 2.hours,
                    commitEndTime = now - 1.hours,
                    revealEndTime = now + 1.hours,
                    endTime = now + 2.hours
                )
                auctionRepo.save(pastCommitAuction)

                val exception = shouldThrow<InvalidAuctionStateException> {
                    service.commitBid(
                        auctionId = pastCommitAuction.id,
                        bidderId = Uuid.generateV7(),
                        blindHash = "some_hash",
                        lockupDeposit = 200_000L
                    )
                }
                exception.message shouldBe "Not in commit phase"
            }
        }

        When("lockupDeposit is less than reserve price") {
            Then("it should throw InvalidDepositException") {
                val now = Clock.System.now()
                val activeCommitAuction = createVickreyAuction(
                    reservePrice = 150_000L,
                    startTime = now - 1.hours,
                    commitEndTime = now + 1.hours,
                    revealEndTime = now + 2.hours,
                    endTime = now + 3.hours
                )
                auctionRepo.save(activeCommitAuction)

                shouldThrow<InvalidDepositException> {
                    service.commitBid(
                        auctionId = activeCommitAuction.id,
                        bidderId = Uuid.generateV7(),
                        blindHash = "some_hash",
                        lockupDeposit = 100_000L // less than 150_000L
                    )
                }
            }
        }

        When("valid first commitment is submitted") {
            Then("it should persist and return a new VickreyBid with unrevealed state") {
                val now = Clock.System.now()
                val activeAuction = createVickreyAuction(
                    reservePrice = 100_000L,
                    startTime = now - 1.hours,
                    commitEndTime = now + 1.hours,
                    revealEndTime = now + 2.hours,
                    endTime = now + 3.hours
                )
                auctionRepo.save(activeAuction)

                val bidderId = Uuid.generateV7()
                val blindHash = computeSha256("180000_secret_salt")
                val deposit = 200_000L

                val committedBid = service.commitBid(
                    auctionId = activeAuction.id,
                    bidderId = bidderId,
                    blindHash = blindHash,
                    lockupDeposit = deposit
                )

                assertSoftly(committedBid) {
                    id.shouldNotBeNull()
                    auctionId shouldBe activeAuction.id
                    this.bidderId shouldBe bidderId
                    this.blindHash shouldBe blindHash
                    lockupDeposit shouldBe deposit
                    actualBid shouldBe null
                    isRevealed shouldBe false
                    revealedAt shouldBe null
                }

                // Verify saved in repository
                val persistedBid = bidRepo.findByAuctionIdAndBidderId(activeAuction.id, bidderId)
                persistedBid shouldBe committedBid
            }
        }

        When("same bidder resubmits commitment during commit phase") {
            Then("it should update the existing commitment without duplicating records") {
                val now = Clock.System.now()
                val activeAuction = createVickreyAuction(
                    reservePrice = 100_000L,
                    startTime = now - 1.hours,
                    commitEndTime = now + 1.hours,
                    revealEndTime = now + 2.hours,
                    endTime = now + 3.hours
                )
                auctionRepo.save(activeAuction)

                val bidderId = Uuid.generateV7()
                val firstHash = computeSha256("150000_salt1")
                val firstBid = service.commitBid(
                    auctionId = activeAuction.id,
                    bidderId = bidderId,
                    blindHash = firstHash,
                    lockupDeposit = 150_000L
                )

                val updatedHash = computeSha256("250000_salt2")
                val updatedBid = service.commitBid(
                    auctionId = activeAuction.id,
                    bidderId = bidderId,
                    blindHash = updatedHash,
                    lockupDeposit = 300_000L
                )

                assertSoftly(updatedBid) {
                    id shouldBe firstBid.id // Preserves existing ID
                    blindHash shouldBe updatedHash
                    lockupDeposit shouldBe 300_000L
                    isRevealed shouldBe false
                }

                bidRepo.countCommitmentsByAuctionId(activeAuction.id) shouldBe 1L
            }
        }
    }

    Given("VickreyAuctionService revealBid phase") {
        val auctionRepo = FakeAuctionRepository()
        val bidRepo = FakeVickreyBidRepository()
        val service = VickreyAuctionService(auctionRepo, bidRepo)

        When("auction does not exist") {
            Then("it should throw AuctionNotFoundException") {
                shouldThrow<AuctionNotFoundException> {
                    service.revealBid(
                        auctionId = Uuid.generateV7(),
                        bidderId = Uuid.generateV7(),
                        actualBid = 150_000L,
                        secretSalt = "salt"
                    )
                }
            }
        }

        When("auction is not a Vickrey auction") {
            Then("it should throw InvalidAuctionTypeException") {
                val now = Clock.System.now()
                val nonVickrey = DummyNonVickreyAuction(
                    startTime = now - 2.hours,
                    endTime = now + 1.hours
                )
                auctionRepo.save(nonVickrey)

                shouldThrow<InvalidAuctionTypeException> {
                    service.revealBid(
                        auctionId = nonVickrey.id,
                        bidderId = Uuid.generateV7(),
                        actualBid = 150_000L,
                        secretSalt = "salt"
                    )
                }
            }
        }

        When("current time is not in reveal window (still in commit phase)") {
            Then("it should throw InvalidAuctionStateException") {
                val now = Clock.System.now()
                val commitPhaseAuction = createVickreyAuction(
                    startTime = now - 1.hours,
                    commitEndTime = now + 1.hours,
                    revealEndTime = now + 2.hours,
                    endTime = now + 3.hours
                )
                auctionRepo.save(commitPhaseAuction)

                val exception = shouldThrow<InvalidAuctionStateException> {
                    service.revealBid(
                        auctionId = commitPhaseAuction.id,
                        bidderId = Uuid.generateV7(),
                        actualBid = 150_000L,
                        secretSalt = "salt"
                    )
                }
                exception.message shouldBe "Not on reveal phase"
            }
        }

        When("current time is after revealEndTime") {
            Then("it should throw InvalidAuctionStateException") {
                val now = Clock.System.now()
                val pastRevealAuction = createVickreyAuction(
                    startTime = now - 3.hours,
                    commitEndTime = now - 2.hours,
                    revealEndTime = now - 1.hours,
                    endTime = now + 1.hours
                )
                auctionRepo.save(pastRevealAuction)

                val exception = shouldThrow<InvalidAuctionStateException> {
                    service.revealBid(
                        auctionId = pastRevealAuction.id,
                        bidderId = Uuid.generateV7(),
                        actualBid = 150_000L,
                        secretSalt = "salt"
                    )
                }
                exception.message shouldBe "Not on reveal phase"
            }
        }

        When("bidder has not committed a bid") {
            Then("it should throw BidNotFoundException") {
                val now = Clock.System.now()
                val revealPhaseAuction = createVickreyAuction(
                    startTime = now - 2.hours,
                    commitEndTime = now - 1.hours,
                    revealEndTime = now + 1.hours,
                    endTime = now + 2.hours
                )
                auctionRepo.save(revealPhaseAuction)

                shouldThrow<BidNotFoundException> {
                    service.revealBid(
                        auctionId = revealPhaseAuction.id,
                        bidderId = Uuid.generateV7(),
                        actualBid = 150_000L,
                        secretSalt = "salt"
                    )
                }
            }
        }

        When("actualBid exceeds committed lockupDeposit") {
            Then("it should throw RevealMismatchException") {
                val now = Clock.System.now()
                val revealPhaseAuction = createVickreyAuction(
                    startTime = now - 2.hours,
                    commitEndTime = now - 1.hours,
                    revealEndTime = now + 1.hours,
                    endTime = now + 2.hours
                )
                auctionRepo.save(revealPhaseAuction)

                val bidderId = Uuid.generateV7()
                val secretSalt = "secure_salt_123"
                val actualBid = 250_000L
                val committedHash = computeSha256("${actualBid}_${secretSalt}")

                val committedBid = VickreyBid(
                    id = Uuid.generateV7(),
                    auctionId = revealPhaseAuction.id,
                    bidderId = bidderId,
                    blindHash = committedHash,
                    lockupDeposit = 200_000L, // Less than actualBid
                    createdAt = now - 90.minutes
                )
                bidRepo.upsert(committedBid)

                shouldThrow<RevealMismatchException> {
                    service.revealBid(
                        auctionId = revealPhaseAuction.id,
                        bidderId = bidderId,
                        actualBid = actualBid,
                        secretSalt = secretSalt
                    )
                }
            }
        }

        When("revealed salt produces mismatched hash") {
            Then("it should throw RevealMismatchException") {
                val now = Clock.System.now()
                val revealPhaseAuction = createVickreyAuction(
                    startTime = now - 2.hours,
                    commitEndTime = now - 1.hours,
                    revealEndTime = now + 1.hours,
                    endTime = now + 2.hours
                )
                auctionRepo.save(revealPhaseAuction)

                val bidderId = Uuid.generateV7()
                val originalSalt = "original_salt"
                val actualBid = 180_000L
                val committedHash = computeSha256("${actualBid}_${originalSalt}")

                val committedBid = VickreyBid(
                    id = Uuid.generateV7(),
                    auctionId = revealPhaseAuction.id,
                    bidderId = bidderId,
                    blindHash = committedHash,
                    lockupDeposit = 200_000L,
                    createdAt = now - 90.minutes
                )
                bidRepo.upsert(committedBid)

                shouldThrow<RevealMismatchException> {
                    service.revealBid(
                        auctionId = revealPhaseAuction.id,
                        bidderId = bidderId,
                        actualBid = actualBid,
                        secretSalt = "tampered_wrong_salt"
                    )
                }
            }
        }

        When("valid actualBid and salt match the committed hash") {
            Then("it should mark the bid as revealed and update actualBid") {
                val now = Clock.System.now()
                val revealPhaseAuction = createVickreyAuction(
                    startTime = now - 2.hours,
                    commitEndTime = now - 1.hours,
                    revealEndTime = now + 1.hours,
                    endTime = now + 2.hours
                )
                auctionRepo.save(revealPhaseAuction)

                val bidderId = Uuid.generateV7()
                val secretSalt = "correct_secret_salt"
                val actualBid = 175_000L
                val committedHash = computeSha256("${actualBid}_${secretSalt}")

                val committedBid = VickreyBid(
                    id = Uuid.generateV7(),
                    auctionId = revealPhaseAuction.id,
                    bidderId = bidderId,
                    blindHash = committedHash,
                    lockupDeposit = 200_000L,
                    createdAt = now - 90.minutes
                )
                bidRepo.upsert(committedBid)

                val revealedBid = service.revealBid(
                    auctionId = revealPhaseAuction.id,
                    bidderId = bidderId,
                    actualBid = actualBid,
                    secretSalt = secretSalt
                )

                assertSoftly(revealedBid) {
                    id shouldBe committedBid.id
                    this.actualBid shouldBe actualBid
                    isRevealed shouldBe true
                    revealedAt.shouldNotBeNull()
                }

                // Verify persisted in repository
                val stored = bidRepo.findByAuctionIdAndBidderId(revealPhaseAuction.id, bidderId)
                stored?.isRevealed shouldBe true
                stored?.actualBid shouldBe actualBid
            }
        }
    }

    Given("VickreyAuctionService settleAuction phase") {
        val auctionRepo = FakeAuctionRepository()
        val bidRepo = FakeVickreyBidRepository()
        val service = VickreyAuctionService(auctionRepo, bidRepo)

        When("auction does not exist") {
            Then("it should throw AuctionNotFoundException") {
                shouldThrow<AuctionNotFoundException> {
                    service.settleAuction(Uuid.generateV7())
                }
            }
        }

        When("auction is not a Vickrey auction") {
            Then("it should throw InvalidAuctionTypeException") {
                val now = Clock.System.now()
                val nonVickrey = DummyNonVickreyAuction(
                    startTime = now - 3.hours,
                    endTime = now - 1.hours
                )
                auctionRepo.save(nonVickrey)

                shouldThrow<InvalidAuctionTypeException> {
                    service.settleAuction(nonVickrey.id)
                }
            }
        }

        When("auction is already settled") {
            Then("it should throw InvalidAuctionStateException") {
                val now = Clock.System.now()
                val settledAuction = createVickreyAuction(
                    status = AuctionStatus.SETTLED,
                    startTime = now - 3.hours,
                    commitEndTime = now - 2.hours,
                    revealEndTime = now - 1.hours,
                    endTime = now - 30.minutes
                )
                auctionRepo.save(settledAuction)

                val exception = shouldThrow<InvalidAuctionStateException> {
                    service.settleAuction(settledAuction.id)
                }
                exception.message shouldBe "Auction is already settled"
            }
        }

        When("current time is before revealEndTime") {
            Then("it should throw InvalidAuctionStateException") {
                val now = Clock.System.now()
                val ongoingAuction = createVickreyAuction(
                    status = AuctionStatus.REVEAL,
                    startTime = now - 2.hours,
                    commitEndTime = now - 1.hours,
                    revealEndTime = now + 30.minutes, // Still in reveal
                    endTime = now + 1.hours
                )
                auctionRepo.save(ongoingAuction)

                val exception = shouldThrow<InvalidAuctionStateException> {
                    service.settleAuction(ongoingAuction.id)
                }
                exception.message shouldBe "Not on settle phase"
            }
        }

        When("auction has 0 revealed bids") {
            Then("it should conclude unsold with status SETTLED, null winner, and null finalPrice") {
                val now = Clock.System.now()
                val auction = createVickreyAuction(
                    status = AuctionStatus.REVEAL,
                    startTime = now - 3.hours,
                    commitEndTime = now - 2.hours,
                    revealEndTime = now - 1.hours,
                    endTime = now - 30.minutes
                )
                auctionRepo.save(auction)

                val settlement = service.settleAuction(auction.id)

                assertSoftly(settlement) {
                    auctionId shouldBe auction.id
                    winnerId shouldBe null
                    finalPrice shouldBe null
                    secondBidderId shouldBe null
                }

                val updatedAuction = auctionRepo.findById(auction.id) as VickreyAuction
                assertSoftly(updatedAuction) {
                    status shouldBe AuctionStatus.SETTLED
                    winnerId shouldBe null
                    finalPrice shouldBe null
                    secondBidderId shouldBe null
                }
            }
        }

        When("auction has 1 revealed bid") {
            Then("it should settle with single bidder as winner paying reserve price") {
                val now = Clock.System.now()
                val reservePrice = 120_000L
                val auction = createVickreyAuction(
                    reservePrice = reservePrice,
                    status = AuctionStatus.REVEAL,
                    startTime = now - 3.hours,
                    commitEndTime = now - 2.hours,
                    revealEndTime = now - 1.hours,
                    endTime = now - 30.minutes
                )
                auctionRepo.save(auction)

                val bidderId = Uuid.generateV7()
                val revealedBid = VickreyBid(
                    id = Uuid.generateV7(),
                    auctionId = auction.id,
                    bidderId = bidderId,
                    blindHash = computeSha256("250000_salt"),
                    lockupDeposit = 300_000L,
                    actualBid = 250_000L,
                    isRevealed = true,
                    createdAt = now - 2.hours,
                    revealedAt = now - 90.minutes
                )
                bidRepo.upsert(revealedBid)

                val settlement = service.settleAuction(auction.id)

                assertSoftly(settlement) {
                    auctionId shouldBe auction.id
                    winnerId shouldBe bidderId
                    finalPrice shouldBe reservePrice
                    secondBidderId shouldBe null
                }

                val updatedAuction = auctionRepo.findById(auction.id) as VickreyAuction
                assertSoftly(updatedAuction) {
                    status shouldBe AuctionStatus.SETTLED
                    winnerId shouldBe bidderId
                    finalPrice shouldBe reservePrice
                    secondBidderId shouldBe null
                }
            }
        }

        When("auction has multiple revealed bids") {
            Then("it should award highest bidder with 2nd highest bid price") {
                val now = Clock.System.now()
                val reservePrice = 100_000L
                val auction = createVickreyAuction(
                    reservePrice = reservePrice,
                    status = AuctionStatus.REVEAL,
                    startTime = now - 3.hours,
                    commitEndTime = now - 2.hours,
                    revealEndTime = now - 1.hours,
                    endTime = now - 30.minutes
                )
                auctionRepo.save(auction)

                val bidder1 = Uuid.generateV7()
                val bidder2 = Uuid.generateV7()
                val bidder3 = Uuid.generateV7()

                bidRepo.upsert(
                    VickreyBid(
                        id = Uuid.generateV7(),
                        auctionId = auction.id,
                        bidderId = bidder1,
                        blindHash = "hash1",
                        lockupDeposit = 400_000L,
                        actualBid = 350_000L,
                        isRevealed = true,
                        createdAt = now - 2.hours,
                        revealedAt = now - 90.minutes
                    )
                )
                bidRepo.upsert(
                    VickreyBid(
                        id = Uuid.generateV7(),
                        auctionId = auction.id,
                        bidderId = bidder2,
                        blindHash = "hash2",
                        lockupDeposit = 300_000L,
                        actualBid = 280_000L,
                        isRevealed = true,
                        createdAt = now - 2.hours,
                        revealedAt = now - 90.minutes
                    )
                )
                bidRepo.upsert(
                    VickreyBid(
                        id = Uuid.generateV7(),
                        auctionId = auction.id,
                        bidderId = bidder3,
                        blindHash = "hash3",
                        lockupDeposit = 200_000L,
                        actualBid = 150_000L,
                        isRevealed = true,
                        createdAt = now - 2.hours,
                        revealedAt = now - 90.minutes
                    )
                )

                val settlement = service.settleAuction(auction.id)

                assertSoftly(settlement) {
                    auctionId shouldBe auction.id
                    winnerId shouldBe bidder1
                    finalPrice shouldBe 280_000L
                    secondBidderId shouldBe bidder2
                }

                val updatedAuction = auctionRepo.findById(auction.id) as VickreyAuction
                assertSoftly(updatedAuction) {
                    status shouldBe AuctionStatus.SETTLED
                    winnerId shouldBe bidder1
                    finalPrice shouldBe 280_000L
                    secondBidderId shouldBe bidder2
                }
            }
        }
    }
})
