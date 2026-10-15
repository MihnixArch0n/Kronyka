package example.kronyka.domain.repository

import example.kronyka.domain.model.Auction
import kotlin.uuid.Uuid

interface AuctionRepository {
    fun findById(id: Uuid): Auction?
    fun save(auction: Auction)
}