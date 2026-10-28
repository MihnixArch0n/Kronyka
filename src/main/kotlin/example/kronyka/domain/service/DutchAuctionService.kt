package example.kronyka.domain.service

import example.kronyka.domain.model.DutchPurchaseConflictException
import example.kronyka.domain.model.AuctionNotFoundException
import example.kronyka.domain.model.DutchAuction
import example.kronyka.domain.model.InvalidAuctionStateException
import example.kronyka.domain.repository.AuctionRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType

class DutchAuctionService(
    private val auctionRepository: AuctionRepository,
    private val priceCalculator: DutchPriceCalculator
) {

    fun getCurrentPrice(
        auctionId: Uuid,
        now: Instant = Clock.System.now()
    ): Long {
        val auction = getDutchAuction(auctionId)

        return priceCalculator.calculatePrice(
            startPrice = auction.startPrice,
            floorPrice = auction.floorPrice,
            stepDecrement = auction.stepDecrement,
            stepIntervalSeconds = auction.stepIntervalSeconds,
            startTime = auction.startTime,
            currentTime = now
        )
    }

    fun createDutchAuction(
        sellerId: Uuid,
        title: String,
        startPrice: Long,
        floorPrice: Long,
        stepDecrement: Long,
        stepIntervalSeconds: Long,
        startTime: Instant,
        endTime: Instant
    ): DutchAuction {

        require(title.isNotBlank()) {
            "Auction title must not be blank"
        }
        require(startPrice > 0) {
            "Start price must be positive"
        }
        require(floorPrice >= 0 && floorPrice <= startPrice) {
            "Invalid floor price"
        }
        require(stepDecrement > 0) {
            "Step decrement must be positive"
        }
        require(stepIntervalSeconds > 0) {
            "Step interval must be positive"
        }
        require(endTime > startTime) {
            "End time must be after start time"
        }

        val now = Clock.System.now()

        val auction = DutchAuction(
            id = Uuid.random(),
            sellerId = sellerId,
            title = title,
            type = AuctionType.DUTCH,
            status = AuctionStatus.ACTIVE,
            startTime = startTime,
            endTime = endTime,
            version = 0L,
            createdAt = now,
            startPrice = startPrice,
            floorPrice = floorPrice,
            stepDecrement = stepDecrement,
            stepIntervalSeconds = stepIntervalSeconds
        )

        auctionRepository.save(auction)

        return auction
    }

    fun buyNow(
        auctionId: Uuid,
        buyerId: Uuid,
        now: Instant = Clock.System.now()
    ): DutchAuction {
        val auction = getDutchAuction(auctionId)

        if (auction.status == AuctionStatus.SOLD) {
            throw DutchPurchaseConflictException(
                "Dutch auction has already been sold"
            )
        }

        auction.ensureCanBuy(now, buyerId)

        val currentPrice = priceCalculator.calculatePrice(
            startPrice = auction.startPrice,
            floorPrice = auction.floorPrice,
            stepDecrement = auction.stepDecrement,
            stepIntervalSeconds = auction.stepIntervalSeconds,
            startTime = auction.startTime,
            currentTime = now
        )

        val success = auctionRepository.tryCompleteDutchPurchase(
            auctionId = auction.id,
            buyerId = buyerId,
            expectedVersion = auction.version,
            finalPrice = currentPrice,
            now = now
        )

        if (!success) {
            throw DutchPurchaseConflictException()
        }

        return getDutchAuction(auctionId)
    }

    private fun getDutchAuction(auctionId: Uuid): DutchAuction {
        val auction = auctionRepository.findById(auctionId)
            ?: throw AuctionNotFoundException()

        return auction as? DutchAuction
            ?: throw InvalidAuctionStateException(
                "Auction is not a Dutch auction"
            )
    }
}
