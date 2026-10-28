package example.kronyka.presentation.controller

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.presentation.dto.*
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import kotlin.uuid.Uuid

@RestController
class AuctionController(
    private val auctionService: AuctionService,
    private val vickreyAuctionService: VickreyAuctionService
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
}
