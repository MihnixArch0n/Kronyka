package example.kronyka.infrastructure.persistence.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object VickreyBidTable : Table() {
    val id = uuid("id")
    val auctionId = reference(
        "auction_id",
        AuctionTable.id,
        onDelete = ReferenceOption.RESTRICT
    )
    val bidderId = reference(
        "bidder_id",
        UserTable.id,
        onDelete = ReferenceOption.RESTRICT
    )
    val blindHash = varchar("blind_hash", 64)
    val lockupDeposit = long("lockup_deposit")
    val actualBid = long("actual_bid").nullable()
    val isRevealed = bool("is_revealed").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val revealedAt = timestamp("revealed_at").nullable()
    init {
        uniqueIndex(
            "idx_user_bid_unique",
            auctionId,
            bidderId
        )
        index(
            "idx_vickrey_bid_settle",
            false,
            auctionId,
            isRevealed,
            actualBid
        )
    }

    override val primaryKey = PrimaryKey(id)
}