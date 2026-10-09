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

    return userHash == blindHash
}

fun calculateSettlement(
    reservePrice: Long,
    auctionId: Uuid,
    revealedBids: List<VickreyBid>
): SettlementResult {
    if (revealedBids.isEmpty())
        return SettlementResult(null, auctionId, null)

    if (revealedBids.size == 1) {
        return SettlementResult(
            winnerId = revealedBids.first().bidderId,
            auctionId = auctionId,
            finalPrice = reservePrice
        )
    }

    val bids = revealedBids.sortedByDescending { it.actualBid }.take(2)
    return SettlementResult(
        winnerId = bids.first().bidderId,
        auctionId = auctionId,
        finalPrice = max(bids.last().actualBid ?: -1, reservePrice),
        secondBidderId = bids.last().bidderId
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

        // TODO: return deposit
    }
}
