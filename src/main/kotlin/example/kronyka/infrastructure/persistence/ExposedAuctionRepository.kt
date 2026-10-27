package example.kronyka.infrastructure.persistence

import example.kronyka.domain.model.Auction
import example.kronyka.domain.model.AuctionPage
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.repository.AuctionRepository
import example.kronyka.infrastructure.persistence.tables.AuctionTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import kotlin.time.Instant
import kotlin.uuid.Uuid

private fun ResultRow.toDomain(): Auction =
    when (this[AuctionTable.type]) {
        AuctionType.VICKREY -> VickreyAuction(
            id = this[AuctionTable.id],
            sellerId = this[AuctionTable.sellerId],
            title = this[AuctionTable.title],
            status = this[AuctionTable.status],
            startTime = this[AuctionTable.startTime],
            endTime = this[AuctionTable.endTime],
            winnerId = this[AuctionTable.winnerId],
            finalPrice = this[AuctionTable.finalPrice],
            version = this[AuctionTable.version],
            createdAt = this[AuctionTable.createdAt],
            reservePrice = checkNotNull(this[AuctionTable.reservePrice]) {
                "Reserve price of auction ${this[AuctionTable.id]} is null"
            },
            commitEndTime = checkNotNull(this[AuctionTable.commitEndTime]) {
                "Commit end time of auction ${this[AuctionTable.id]} is null"
            },
            revealEndTime = checkNotNull(this[AuctionTable.revealEndTime]) {
                "Reveal end time of auction ${this[AuctionTable.id]} is null"
            },
            secondBidderId = this[AuctionTable.secondBidderId]
        )
        AuctionType.DUTCH -> TODO("Not implemented yet")
    }

@Repository
class ExposedAuctionRepository : AuctionRepository {
    @Transactional(readOnly = true)
    override fun findAll(type: AuctionType?, status: AuctionStatus?, offset: Long, limit: Int): AuctionPage {
        val query = AuctionTable.selectAll()
        type?.let { selectedType -> query.andWhere { AuctionTable.type eq selectedType } }
        status?.let { selectedStatus -> query.andWhere { AuctionTable.status eq selectedStatus } }

        val total = query.count()
        val items = query
            .orderBy(AuctionTable.createdAt to SortOrder.DESC, AuctionTable.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toDomain() }
        return AuctionPage(items, total)
    }

    @Transactional
    override fun findById(id: Uuid): Auction? =
        AuctionTable.selectAll().where {
            AuctionTable.id eq id
        }
            .singleOrNull()
            ?.toDomain()

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

    @Transactional(readOnly = true)
    override fun findExpiredUnsettledVickreyAuctions(beforeTime: Instant, limit: Int): List<VickreyAuction> =
        AuctionTable.selectAll().where {
            (AuctionTable.type eq AuctionType.VICKREY) and
            (AuctionTable.status notInList listOf(AuctionStatus.SETTLED, AuctionStatus.CANCELLED)) and
            (AuctionTable.revealEndTime lessEq beforeTime)
        }
            .orderBy(AuctionTable.revealEndTime, SortOrder.ASC)
            .limit(limit)
            .map { it.toDomain() as VickreyAuction }
}