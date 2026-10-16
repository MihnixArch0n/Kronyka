package example.kronyka.infrastructure.persistence

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.repository.AuctionRepository
import example.kronyka.infrastructure.persistence.tables.AuctionTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import kotlin.uuid.Uuid

@Repository
class ExposedAuctionRepository : AuctionRepository {
    @Transactional
    override fun findById(id: Uuid): Auction? {
        val auction = AuctionTable.selectAll().where {
            AuctionTable.id eq id
        }
            .singleOrNull()
            ?.let {
                when (it[AuctionTable.type]) {
                    AuctionType.VICKREY -> VickreyAuction(
                        id = it[AuctionTable.id],
                        sellerId = it[AuctionTable.sellerId],
                        title = it[AuctionTable.title],
                        status = it[AuctionTable.status],
                        startTime = it[AuctionTable.startTime],
                        endTime = it[AuctionTable.endTime],
                        winnerId = it[AuctionTable.winnerId],
                        finalPrice = it[AuctionTable.finalPrice],
                        version = it[AuctionTable.version],
                        createdAt = it[AuctionTable.createdAt],
                        reservePrice = checkNotNull(it[AuctionTable.reservePrice]) {
                            "Reserve price of bid ${it[AuctionTable.id]} is null"
                        },
                        commitEndTime = checkNotNull(it[AuctionTable.commitEndTime]) {
                            "Commit end time of bid ${it[AuctionTable.id]} is null"
                        },
                        revealEndTime = checkNotNull(it[AuctionTable.revealEndTime]) {
                            "Reveal end time of bid ${it[AuctionTable.id]} is null"
                        },
                        secondBidderId = it[AuctionTable.secondBidderId]
                    )

                    AuctionType.DUTCH -> TODO("Not implemented yet")
                }
            }
        return auction
    }

    @Transactional
    override fun save(auction: Auction) {
        AuctionTable.upsert {
            it[AuctionTable.id] = auction.id
            it[AuctionTable.sellerId] = auction.sellerId
            it[AuctionTable.title] = auction.title
            it[AuctionTable.type] = auction.type
            it[AuctionTable.status] = auction.status
            when (auction) {
                is VickreyAuction -> {
                    it[AuctionTable.reservePrice] = auction.reservePrice
                    it[AuctionTable.commitEndTime] = auction.commitEndTime
                    it[AuctionTable.revealEndTime] = auction.revealEndTime
                    it[AuctionTable.secondBidderId] = auction.secondBidderId
                }
                else -> TODO("Not implemented yet")
            }
            it[AuctionTable.startTime] = auction.startTime
            it[AuctionTable.finalPrice] = auction.finalPrice
            it[AuctionTable.endTime] = auction.endTime
            it[AuctionTable.winnerId] = auction.winnerId
            it[AuctionTable.version] = auction.version
            it[AuctionTable.createdAt] = auction.createdAt
        }
    }
}