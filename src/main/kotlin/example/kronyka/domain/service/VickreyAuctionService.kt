package example.kronyka.domain.service

import example.kronyka.domain.model.*
import example.kronyka.domain.repository.AuctionRepository
import example.kronyka.domain.repository.VickreyBidRepository
import java.security.MessageDigest
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun matchesBlindHash(blindHash: String, actualBid: Long, salt: String): Boolean {
    val userString = "${actualBid}_${salt}"
    val userHash = MessageDigest.getInstance("SHA-256")
        .digest(userString.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    return blindHash.equals(userHash, ignoreCase = true)
}

fun calculateSettlement(
    reservePrice: Long,
    auctionId: Uuid,
    revealedBids: List<VickreyBid>
): SettlementResult {
    val qualifyingBids = revealedBids
        .filter { it.actualBid != null && it.actualBid >= reservePrice }
        .sortedWith(
            compareByDescending<VickreyBid> { it.actualBid }
                .thenBy { it.createdAt }
        )

    if (qualifyingBids.isEmpty())
        return SettlementResult(winnerId = null, auctionId = auctionId, finalPrice = null, secondBidderId = null)

    if (qualifyingBids.size == 1) {
        return SettlementResult(
            winnerId = qualifyingBids.first().bidderId,
            auctionId = auctionId,
            finalPrice = reservePrice,
            secondBidderId = null
        )
    }

    val topBids = qualifyingBids.take(2)
    val secondBidPrice = topBids[1].actualBid ?: reservePrice
    return SettlementResult(
        winnerId = topBids[0].bidderId,
        auctionId = auctionId,
        finalPrice = max(secondBidPrice, reservePrice),
        secondBidderId = topBids[1].bidderId
    )
}


class VickreyAuctionService(
    private val auctionRepository: AuctionRepository,
    private val bidRepository: VickreyBidRepository
) {
    @OptIn(ExperimentalUuidApi::class)
    fun createVickreyAuction(
        sellerId: Uuid,
        title: String,
        startTime: Instant,
        reservePrice: Long,
        commitEndTime: Instant,
        revealEndTime: Instant,
    ): VickreyAuction {
        if (reservePrice < 0) throw InvalidReservePriceException()

        val now = Clock.System.now()

        if (now > startTime + 10.seconds
            || startTime >= commitEndTime
            || commitEndTime >= revealEndTime)
            throw InvalidAuctionTimeException()

        val auction = VickreyAuction(
            id = Uuid.generateV7(),
            sellerId = sellerId,
            title = title,
            type = AuctionType.VICKREY,
            status = AuctionStatus.COMMIT,
            startTime = startTime,
            endTime = revealEndTime,
            version = 0L,
            createdAt = now,
            reservePrice = reservePrice,
            commitEndTime = commitEndTime,
            revealEndTime = revealEndTime,
        )
        auctionRepository.save(auction)
        return auction
    }
    private fun getVickreyAuction(auctionId: Uuid): VickreyAuction {
        val auction = auctionRepository.findById(auctionId)
            ?: throw AuctionNotFoundException()

        if (auction !is VickreyAuction)
            throw InvalidAuctionTypeException()

        return auction
    }

    @OptIn(ExperimentalUuidApi::class)
    fun commitBid(auctionId: Uuid,
                  bidderId: Uuid,
                  blindHash: String,
                  lockupDeposit: Long
    ): VickreyBid {
        val auction = getVickreyAuction(auctionId)
        val currentTime = Clock.System.now()
        auction.ensureCommitPhase(currentTime)

        if (lockupDeposit < auction.reservePrice) throw InvalidDepositException()

        val bid = bidRepository.findByAuctionIdAndBidderId(auctionId, bidderId)
            ?.copy(blindHash = blindHash, lockupDeposit = lockupDeposit)
            ?: VickreyBid(
                id = Uuid.generateV7(),
                auctionId = auction.id,
                bidderId = bidderId,
                blindHash = blindHash,
                lockupDeposit = lockupDeposit,
                createdAt = currentTime
            )
        bidRepository.upsert(bid)
        return bid
    }

    fun revealBid(auctionId: Uuid,
                  bidderId: Uuid,
                  actualBid: Long,
                  secretSalt: String
    ): VickreyBid {
        val auction = getVickreyAuction(auctionId)
        val currentTime = Clock.System.now()
        auction.ensureRevealPhase(currentTime)

        val bid = bidRepository.findByAuctionIdAndBidderId(auctionId, bidderId)
            ?: throw BidNotFoundException()

        if (actualBid > bid.lockupDeposit) throw RevealMismatchException()

        if (!matchesBlindHash(bid.blindHash, actualBid, secretSalt)) throw RevealMismatchException()

        val newBid = bid.copy(
            actualBid = actualBid,
            isRevealed = true,
            revealedAt = currentTime
        )
        bidRepository.upsert(newBid)
        return newBid
    }

    fun settleAuction(auctionId: Uuid): SettlementResult {
        val auction = getVickreyAuction(auctionId)
        val currentTime = Clock.System.now()
        auction.ensureSettlePhase(currentTime)

        val bids = bidRepository.findAllRevealedBidsByAuctionId(auction.id)
        val results = calculateSettlement(
            reservePrice = auction.reservePrice,
            auctionId = auction.id,
            revealedBids = bids
        )
        auctionRepository.save(auction.copy(
            status = AuctionStatus.SETTLED,
            winnerId = results.winnerId,
            finalPrice = results.finalPrice,
            secondBidderId = results.secondBidderId,
        ))
        return results
    }

    fun getCommitmentCount(auctionId: Uuid): Long =
        bidRepository.countCommitmentsByAuctionId(auctionId)

    fun validateCanCancel(auctionId: Uuid) {
        if (bidRepository.countCommitmentsByAuctionId(auctionId) > 0) {
            throw AuctionCancellationException()
        }
    }
}
