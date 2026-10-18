package example.kronyka.domain.service

import example.kronyka.domain.model.*
import example.kronyka.domain.repository.AuctionRepository
import example.kronyka.domain.repository.VickreyBidRepository
import java.security.MessageDigest
import kotlin.math.max
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun matchesBlindHash(blindHash: String, actualBid: Long, salt: String): Boolean {
    val userString = "${actualBid}_${salt}"
    val userHash = MessageDigest.getInstance("SHA-256")
        .digest(userString.toByteArray())
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
    fun commitBid(auctionId: Uuid,
                  bidderId: Uuid,
                  blindHash: String,
                  lockupDeposit: Long
    ): VickreyBid {
        val auction = auctionRepository.findById(auctionId)
            ?: throw AuctionNotFoundException()

        if (auction !is VickreyAuction)
            throw InvalidAuctionTypeException()

        if (auction.status == AuctionStatus.CANCELLED || auction.status == AuctionStatus.SETTLED)
            throw InvalidAuctionStateException("Auction is ${auction.status.name.lowercase()}")

        val currentTime = Clock.System.now()
        if (currentTime !in auction.startTime..<auction.commitEndTime)
            throw InvalidAuctionStateException("Not in commit phase")

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
        val auction = auctionRepository.findById(auctionId)
            ?: throw AuctionNotFoundException()

        if (auction !is VickreyAuction) throw InvalidAuctionTypeException()

        if (auction.status == AuctionStatus.CANCELLED || auction.status == AuctionStatus.SETTLED)
            throw InvalidAuctionStateException("Auction is ${auction.status.name.lowercase()}")

        val currentTime = Clock.System.now()
        if (currentTime !in auction.commitEndTime..<auction.revealEndTime)
            throw InvalidAuctionStateException("Not on reveal phase")

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
        val auction = auctionRepository.findById(auctionId)
            ?: throw AuctionNotFoundException()

        if (auction !is VickreyAuction) throw InvalidAuctionTypeException()

        if (auction.status == AuctionStatus.CANCELLED)
            throw InvalidAuctionStateException("Auction is cancelled")

        if (auction.status == AuctionStatus.SETTLED)
            throw InvalidAuctionStateException("Auction is already settled")

        if (Clock.System.now() < auction.revealEndTime)
            throw InvalidAuctionStateException("Not on settle phase")

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
}
