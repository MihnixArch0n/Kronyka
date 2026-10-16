package example.kronyka.infrastructure.persistence

import example.kronyka.domain.model.VickreyBid
import example.kronyka.domain.repository.VickreyBidRepository
import example.kronyka.infrastructure.persistence.tables.VickreyBidTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import kotlin.uuid.Uuid

private fun ResultRow.toDomain(): VickreyBid =
    VickreyBid(
        id = this[VickreyBidTable.id],
        auctionId = this[VickreyBidTable.auctionId],
        bidderId = this[VickreyBidTable.bidderId],
        blindHash = this[VickreyBidTable.blindHash],
        lockupDeposit = this[VickreyBidTable.lockupDeposit],
        actualBid = this[VickreyBidTable.actualBid],
        isRevealed = this[VickreyBidTable.isRevealed],
        createdAt = this[VickreyBidTable.createdAt],
        revealedAt = this[VickreyBidTable.revealedAt]
    )

@Repository
class ExposedVickreyBidRepository : VickreyBidRepository {
    @Transactional
    override fun findByAuctionIdAndBidderId(
        auctionId: Uuid,
        bidderId: Uuid
    ): VickreyBid? {
        val bid = VickreyBidTable.selectAll().where {
            (VickreyBidTable.auctionId eq auctionId) and
                    (VickreyBidTable.bidderId eq bidderId)
        }
            .singleOrNull()
            ?.toDomain()

        return bid
    }

    @Transactional
    override fun upsert(bid: VickreyBid) {
        VickreyBidTable.upsert(keys = arrayOf(
            VickreyBidTable.auctionId,
            VickreyBidTable.bidderId
        )) {
            it[id] = bid.id
            it[auctionId] = bid.auctionId
            it[bidderId] = bid.bidderId
            it[blindHash] = bid.blindHash
            it[lockupDeposit] = bid.lockupDeposit
            it[actualBid] = bid.actualBid
            it[isRevealed] = bid.isRevealed
            it[createdAt] = bid.createdAt
            it[revealedAt] = bid.revealedAt
        }
    }

    @Transactional
    override fun findAllRevealedBidsByAuctionId(auctionId: Uuid): List<VickreyBid> {
        val bids = VickreyBidTable.selectAll().where {
            (VickreyBidTable.auctionId eq auctionId) and
                    (VickreyBidTable.isRevealed eq true)
        }.map { it.toDomain() }
        return bids
    }

    @Transactional
    override fun countCommitmentsByAuctionId(auctionId: Uuid): Long {
        return VickreyBidTable.selectAll().where {
            VickreyBidTable.auctionId eq auctionId
        }.count()
    }
}