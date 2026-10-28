package example.kronyka.infrastructure.config

import example.kronyka.domain.repository.AuctionRepository
import example.kronyka.domain.repository.VickreyBidRepository
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.VickreyAuctionService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import example.kronyka.domain.service.DutchAuctionService
import example.kronyka.domain.service.DutchPriceCalculator

@Configuration
class AuctionConfig {
    @Bean
    fun auctionService(
        auctionRepository: AuctionRepository,
        vickreyAuctionService: VickreyAuctionService
    ): AuctionService = AuctionService(auctionRepository, vickreyAuctionService)

    @Bean
    fun vickreyAuctionService(
        auctionRepository: AuctionRepository,
        bidRepository: VickreyBidRepository
    ): VickreyAuctionService = VickreyAuctionService(auctionRepository, bidRepository)
    @Bean
    fun dutchPriceCalculator(): DutchPriceCalculator =
        DutchPriceCalculator()

    @Bean
    fun dutchAuctionService(
        auctionRepository: AuctionRepository,
        dutchPriceCalculator: DutchPriceCalculator
    ): DutchAuctionService =
        DutchAuctionService(auctionRepository, dutchPriceCalculator)
}
