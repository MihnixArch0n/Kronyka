package example.kronyka.presentation.controller

import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.presentation.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import kotlin.uuid.Uuid

@RestController
@RequestMapping("/api")
class AuctionController(
    private val auctionService: AuctionService,
    private val vickreyAuctionService: VickreyAuctionService
) {
    @PostMapping("/auctions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createVickreyAuction(
        @RequestHeader("X-User-Id") sellerId: Uuid,
        @Valid @RequestBody request: CreateVickreyAuctionRequest
    ): ApiResponse<CreateAuctionResponse> {
        val vickreyAuction = auctionService.createVickreyAuction(
            sellerId = sellerId,
            title = request.title!!,
            startTime = request.startTime!!,
            reservePrice = request.reservePrice!!,
            commitEndTime = request.commitEndTime!!,
            revealEndTime = request.revealEndTime!!,
        )

        return ApiResponse.success(vickreyAuction.toCreateResponse())
    }

    @GetMapping("/auctions/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getAuction(@PathVariable id: Uuid): ApiResponse<AuctionDetailResponse> {
        val auction = auctionService.getAuction(id)
        val totalCommitments = if (auction is VickreyAuction) {
            auctionService.getAuctionCommitmentCount(id)
        } else null

        return ApiResponse.success(auction.toDetailResponse(totalCommitments))
    }

    @DeleteMapping("/auctions/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    fun cancelAuction(
        @PathVariable id: Uuid,
        @RequestHeader("X-User-Id") sellerId: Uuid,
    ): ApiResponse<CancelAuctionResponse> {
        auctionService.cancelAuction(auctionId = id, userId = sellerId)
        return ApiResponse.success(CancelAuctionResponse(auctionId = id))
    }

    @PostMapping("/auctions/{id}/commit")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun commitBid(
        @PathVariable id: Uuid,
        @RequestHeader("X-User-Id") bidderId: Uuid,
        @Valid @RequestBody request: CommitBidRequest
    ): ApiResponse<CommitBidResponse> {
        val bid = vickreyAuctionService.commitBid(
            auctionId = id,
            bidderId = bidderId,
            blindHash = request.blindHash!!,
            lockupDeposit = request.lockupDeposit!!
        )

        return ApiResponse.success(bid.toCommitResponse())
    }

    @PostMapping("/auctions/{id}/reveal")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    fun revealBid(
        @PathVariable id: Uuid,
        @RequestHeader("X-User-Id") bidderId: Uuid,
        @Valid @RequestBody request: RevealBidRequest
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