package example.kronyka.infrastructure.config

import example.kronyka.domain.repository.AuctionRepository
import example.kronyka.domain.repository.VickreyBidRepository
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.VickreyAuctionService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuctionConfig {
    @Bean
    fun auctionService(
        auctionRepository: AuctionRepository,
        bidRepository: VickreyBidRepository
    ): AuctionService = AuctionService(auctionRepository, bidRepository)

    @Bean
    fun vickreyAuctionService(
        auctionRepository: AuctionRepository,
        bidRepository: VickreyBidRepository
    ): VickreyAuctionService = VickreyAuctionService(auctionRepository, bidRepository)
}
