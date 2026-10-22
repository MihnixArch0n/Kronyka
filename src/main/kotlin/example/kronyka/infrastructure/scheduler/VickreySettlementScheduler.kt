package example.kronyka.infrastructure.scheduler

import example.kronyka.domain.repository.AuctionRepository
import example.kronyka.domain.service.VickreyAuctionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.time.Clock

@Component
@ConditionalOnProperty(
    prefix = "kronyka.auction.settlement",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class VickreySettlementScheduler(
    private val auctionRepository: AuctionRepository,
    private val vickreyAuctionService: VickreyAuctionService,
    @Value("\${kronyka.auction.settlement.batch-size:50}") private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(VickreySettlementScheduler::class.java)

    @Scheduled(fixedDelayString = "\${kronyka.auction.settlement.fixed-delay-ms:5000}")
    fun settleExpiredAuctions() {
        val now = Clock.System.now()
        val expiredAuctions = auctionRepository.findExpiredUnsettledVickreyAuctions(
            beforeTime = now,
            limit = batchSize
        )

        if (expiredAuctions.isEmpty()) {
            return
        }

        logger.info("Found {} expired Vickrey auction(s) to settle", expiredAuctions.size)

        for (auction in expiredAuctions) {
            try {
                val result = vickreyAuctionService.settleAuction(auction.id)
                logger.info(
                    "Successfully settled Vickrey auction {}: winner={}, finalPrice={}, secondBidder={}",
                    auction.id,
                    result.winnerId,
                    result.finalPrice,
                    result.secondBidderId
                )
            } catch (ex: Exception) {
                logger.error("Failed to settle Vickrey auction {}: {}", auction.id, ex.message, ex)
            }
        }
    }
}
