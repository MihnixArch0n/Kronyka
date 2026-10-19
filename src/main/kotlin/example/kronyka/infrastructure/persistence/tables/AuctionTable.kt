package example.kronyka.infrastructure.persistence.tables

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object AuctionTable : Table() {
    val id = uuid("id")
    val sellerId = reference(
        "seller_id",
        UserTable.id,
        onDelete = ReferenceOption.RESTRICT,
    )
    val title = varchar("title", 255)
    val type = enumerationByName("type", 10, AuctionType::class)
    val status = enumerationByName("status", 20, AuctionStatus::class)
    val startPrice = long("start_price").nullable()
    val floorPrice = long("floor_price").nullable()
    val stepDecrement = long("step_decrement").nullable()
    val stepIntervalSeconds = long("step_interval_seconds").nullable()
    val reservePrice = long("reserve_price").nullable()
    val startTime = timestamp("start_time")
    val commitEndTime = timestamp("commit_end_time").nullable()
    val revealEndTime = timestamp("reveal_end_time").nullable()
    val endTime = timestamp("end_time")
    val winnerId = optReference(
        "winner_id",
        UserTable.id,
        onDelete = ReferenceOption.RESTRICT,
    )
    val secondBidderId = optReference(
        "second_bidder_id",
        UserTable.id,
        onDelete = ReferenceOption.RESTRICT,
    )
    val finalPrice = long("final_price").nullable()
    val version = long("version")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    init {
        index(
            "idx_auction_type_status",
            false,
            type,
            status
        )
    }

    override val primaryKey = PrimaryKey(id)
}