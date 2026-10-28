package example.kronyka.presentation.controller

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.DutchAuctionService
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.presentation.dto.*
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import kotlin.uuid.Uuid

@RestController
class AuctionController(
    private val auctionService: AuctionService,
    private val vickreyAuctionService: VickreyAuctionService,
    private val dutchAuctionService: DutchAuctionService
) : AuctionApi {

    override fun getAuctions(
        type: AuctionType?,
        status: AuctionStatus?,
        page: Int,
        size: Int
    ): ApiResponse<AuctionPageResponse> {
        val auctions = auctionService.getAuctions(type, status, page, size)
        return ApiResponse.success(
            AuctionPageResponse(total = auctions.total, items = auctions.items.map { it.toDetailResponse() })
        )
    }

    @Transactional
    override fun createVickreyAuction(
        sellerId: Uuid,
        request: CreateVickreyAuctionRequest
    ): ApiResponse<CreateAuctionResponse> {
        val vickreyAuction = vickreyAuctionService.createVickreyAuction(
            sellerId = sellerId,
            title = request.title!!,
            startTime = request.startTime!!,
            reservePrice = request.reservePrice!!,
            commitEndTime = request.commitEndTime!!,
            revealEndTime = request.revealEndTime!!,
        )

        return ApiResponse.success(vickreyAuction.toCreateResponse())
    }

    override fun getAuction(id: Uuid): ApiResponse<AuctionDetailResponse> {
        val auction = auctionService.getAuction(id)
        val totalCommitments = if (auction is VickreyAuction) {
            vickreyAuctionService.getCommitmentCount(id)
        } else null

        return ApiResponse.success(auction.toDetailResponse(totalCommitments))
    }

    @Transactional
    override fun cancelAuction(
        id: Uuid,
        sellerId: Uuid
    ): ApiResponse<CancelAuctionResponse> {
        auctionService.cancelAuction(auctionId = id, userId = sellerId)
        return ApiResponse.success(CancelAuctionResponse(auctionId = id))
    }

    @Transactional
    override fun commitBid(
        id: Uuid,
        bidderId: Uuid,
        request: CommitBidRequest
    ): ApiResponse<CommitBidResponse> {
        val bid = vickreyAuctionService.commitBid(
            auctionId = id,
            bidderId = bidderId,
            blindHash = request.blindHash!!,
            lockupDeposit = request.lockupDeposit!!
        )

        return ApiResponse.success(bid.toCommitResponse())
    }

    @Transactional
    override fun revealBid(
        id: Uuid,
        bidderId: Uuid,
        request: RevealBidRequest
    ): ApiResponse<RevealBidResponse> {
        val bid = vickreyAuctionService.revealBid(
            auctionId = id,
            bidderId = bidderId,
            actualBid = request.actualBid!!,
            secretSalt = request.secretSalt!!
        )

        return ApiResponse.success(bid.toRevealResponse())
    }

    @Transactional
    override fun createDutchAuction(
        sellerId: Uuid,
        request: CreateDutchAuctionRequest
    ): ApiResponse<CreateAuctionResponse> {
        val auction = dutchAuctionService.createDutchAuction(
            sellerId = sellerId,
            title = request.title!!,
            startPrice = request.startPrice!!,
            floorPrice = request.floorPrice!!,
            stepDecrement = request.stepDecrement!!,
            stepIntervalSeconds = request.stepIntervalSeconds!!,
            startTime = request.startTime!!,
            endTime = request.endTime!!
        )

        return ApiResponse.success(
            CreateAuctionResponse(
                auctionId = auction.id,
                title = auction.title,
                type = auction.type,
                sellerId = auction.sellerId,
                startTime = auction.startTime,
                endTime = auction.endTime,
                status = auction.status.name
            )
        )
    }

    override fun getDutchPrice(id: Uuid): ApiResponse<DutchPriceResponse> {
        val price = dutchAuctionService.getCurrentPrice(id)

        return ApiResponse.success(
            DutchPriceResponse(
                auctionId = id,
                currentPrice = price
            )
        )
    }

    @Transactional
    override fun buyDutchAuction(
        id: Uuid,
        buyerId: Uuid
    ): ApiResponse<DutchBuyResponse> {
        val auction = dutchAuctionService.buyNow(
            auctionId = id,
            buyerId = buyerId
        )

        return ApiResponse.success(auction.toDutchBuyResponse())
    }
}
