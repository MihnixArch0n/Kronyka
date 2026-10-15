package example.kronyka.domain.service

import example.kronyka.domain.model.VickreyBid
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class VickreyEngineMathSpec : FunSpec({

    fun computeSha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun createBid(
        bidderId: Uuid,
        auctionId: Uuid,
        actualBid: Long?,
        lockupDeposit: Long = 200_000L,
        isRevealed: Boolean = true
    ): VickreyBid {
        val now = Clock.System.now()
        val blindHash = computeSha256("${actualBid ?: 0}_salt")
        return VickreyBid(
            id = Uuid.generateV7(),
            auctionId = auctionId,
            bidderId = bidderId,
            blindHash = blindHash,
            lockupDeposit = lockupDeposit,
            actualBid = actualBid,
            isRevealed = isRevealed,
            createdAt = now,
            revealedAt = if (isRevealed) now else null
        )
    }

    context("matchesBlindHash") {
        test("should return true when actualBid and salt match the SHA-256 blind hash") {
            val actualBid = 150_000L
            val salt = "secret_phrase_xyz"
            val expectedHash = computeSha256("${actualBid}_${salt}")

            matchesBlindHash(
                blindHash = expectedHash,
                actualBid = actualBid,
                salt = salt
            ) shouldBe true
        }

        test("should return false when actualBid differs from committed hash") {
            val actualBid = 150_000L
            val salt = "secret_phrase_xyz"
            val hash = computeSha256("${actualBid}_${salt}")

            matchesBlindHash(
                blindHash = hash,
                actualBid = 200_000L,
                salt = salt
            ) shouldBe false
        }

        test("should return false when salt differs from committed hash") {
            val actualBid = 150_000L
            val hash = computeSha256("${actualBid}_correct_salt")

            matchesBlindHash(
                blindHash = hash,
                actualBid = actualBid,
                salt = "wrong_salt"
            ) shouldBe false
        }

        test("should return false when hash case does not match standard lowercase hex") {
            val actualBid = 150_000L
            val salt = "salt"
            val uppercaseHash = computeSha256("${actualBid}_${salt}").uppercase()

            matchesBlindHash(
                blindHash = uppercaseHash,
                actualBid = actualBid,
                salt = salt
            ) shouldBe false
        }
    }

    context("calculateSettlement") {
        val auctionId = Uuid.generateV7()
        val reservePrice = 50_000L

        test("should return null winner and final price when revealed bids list is empty") {
            val result = calculateSettlement(
                reservePrice = reservePrice,
                auctionId = auctionId,
                revealedBids = emptyList()
            )

            assertSoftly(result) {
                auctionId shouldBe auctionId
                winnerId shouldBe null
                finalPrice shouldBe null
                secondBidderId shouldBe null
            }
        }

        test("should award single revealed bidder at reserve price with null secondBidderId") {
            val bidderId = Uuid.generateV7()
            val singleBid = createBid(bidderId = bidderId, auctionId = auctionId, actualBid = 120_000L)

            val result = calculateSettlement(
                reservePrice = reservePrice,
                auctionId = auctionId,
                revealedBids = listOf(singleBid)
            )

            assertSoftly(result) {
                auctionId shouldBe auctionId
                winnerId shouldBe bidderId
                finalPrice shouldBe reservePrice
                secondBidderId shouldBe null
            }
        }

        test("should award highest bidder with 2nd highest bid price when 2nd bid exceeds reserve price") {
            val bidder1 = Uuid.generateV7()
            val bidder2 = Uuid.generateV7()
            val bid1 = createBid(bidderId = bidder1, auctionId = auctionId, actualBid = 200_000L)
            val bid2 = createBid(bidderId = bidder2, auctionId = auctionId, actualBid = 140_000L)

            // pass in unsorted order to verify calculation sorts bids
            val result = calculateSettlement(
                reservePrice = reservePrice,
                auctionId = auctionId,
                revealedBids = listOf(bid2, bid1)
            )

            assertSoftly(result) {
                auctionId shouldBe auctionId
                winnerId shouldBe bidder1
                finalPrice shouldBe 140_000L
                secondBidderId shouldBe bidder2
            }
        }

        test("should fallback to reserve price if second highest bid is below reserve price") {
            val bidder1 = Uuid.generateV7()
            val bidder2 = Uuid.generateV7()
            val highReservePrice = 100_000L
            val bid1 = createBid(bidderId = bidder1, auctionId = auctionId, actualBid = 150_000L)
            val bid2 = createBid(bidderId = bidder2, auctionId = auctionId, actualBid = 80_000L)

            val result = calculateSettlement(
                reservePrice = highReservePrice,
                auctionId = auctionId,
                revealedBids = listOf(bid1, bid2)
            )

            assertSoftly(result) {
                auctionId shouldBe auctionId
                winnerId shouldBe bidder1
                finalPrice shouldBe highReservePrice
                secondBidderId shouldBe bidder2
            }
        }

        test("should select 1st and 2nd highest bids from multiple revealed bids") {
            val bidder1 = Uuid.generateV7()
            val bidder2 = Uuid.generateV7()
            val bidder3 = Uuid.generateV7()
            val bidder4 = Uuid.generateV7()

            val bid1 = createBid(bidderId = bidder1, auctionId = auctionId, actualBid = 300_000L)
            val bid2 = createBid(bidderId = bidder2, auctionId = auctionId, actualBid = 250_000L)
            val bid3 = createBid(bidderId = bidder3, auctionId = auctionId, actualBid = 180_000L)
            val bid4 = createBid(bidderId = bidder4, auctionId = auctionId, actualBid = 100_000L)

            val result = calculateSettlement(
                reservePrice = reservePrice,
                auctionId = auctionId,
                revealedBids = listOf(bid3, bid1, bid4, bid2)
            )

            assertSoftly(result) {
                auctionId shouldBe auctionId
                winnerId shouldBe bidder1
                finalPrice shouldBe 250_000L
                secondBidderId shouldBe bidder2
            }
        }

        test("should determine settlement price when top two bids are tied") {
            val bidder1 = Uuid.generateV7()
            val bidder2 = Uuid.generateV7()
            val bid1 = createBid(bidderId = bidder1, auctionId = auctionId, actualBid = 200_000L)
            val bid2 = createBid(bidderId = bidder2, auctionId = auctionId, actualBid = 200_000L)

            val result = calculateSettlement(
                reservePrice = reservePrice,
                auctionId = auctionId,
                revealedBids = listOf(bid1, bid2)
            )

            assertSoftly(result) {
                auctionId shouldBe auctionId
                finalPrice shouldBe 200_000L
                (winnerId == bidder1 || winnerId == bidder2) shouldBe true
                (secondBidderId == bidder1 || secondBidderId == bidder2) shouldBe true
            }
        }
    }
})
