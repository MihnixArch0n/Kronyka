package example.kronyka.presentation.controller

import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.presentation.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
    fun createVickreyAuction(
        @RequestHeader("X-User-Id") sellerId: Uuid,
        @Valid @RequestBody request: CreateVickreyAuctionRequest
    ): ApiResponse<CreateAuctionResponse> {
        val vickreyAuction = auctionService.createVickreyAuction(
            sellerId = sellerId,
            title = requireNotNull(request.title) { "Title is required" },
            startTime = requireNotNull(request.startTime) { "Start time is required" },
            reservePrice = requireNotNull(request.reservePrice) { "Reserve price is required" },
            commitEndTime = requireNotNull(request.commitEndTime) { "Commit end time is required" },
            revealEndTime = requireNotNull(request.revealEndTime) { "Reveal end time is required" },
        )

        return ApiResponse.success(vickreyAuction.toCreateResponse())
    }

    @GetMapping("/auctions/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getAuction(@PathVariable id: Uuid): ApiResponse<AuctionDetailResponse> {
        val auction = auctionService.getAuction(id)

        return ApiResponse.success(auction.toDetailResponse())
    }

    @DeleteMapping("/auctions/{id}")
    fun cancelAuction(
        @PathVariable id: Uuid,
        @RequestHeader("X-User-Id") sellerId: Uuid,
    ): ApiResponse<Unit> {
        auctionService.cancelAuction(auctionId = id, userId = sellerId)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/auctions/{id}/commit")
    @ResponseStatus(HttpStatus.OK)
    fun commitBid(
        @PathVariable id: Uuid,
        @RequestHeader("X-User-Id") bidderId: Uuid,
        @Valid @RequestBody request: CommitBidRequest
    ): ApiResponse<CommitBidResponse> {
        val bid = vickreyAuctionService.commitBid(
            auctionId = id,
            bidderId = bidderId,
            blindHash = requireNotNull(request.blindHash) { "Blind hash is required" },
            lockupDeposit = requireNotNull(request.lockupDeposit) { "Lockup deposit is required" }
        )

        return ApiResponse.success(bid.toCommitResponse())
    }

    @PostMapping("/auctions/{id}/reveal")
    @ResponseStatus(HttpStatus.OK)
    fun revealBid(
        @PathVariable id: Uuid,
        @RequestHeader("X-User-Id") bidderId: Uuid,
        @Valid @RequestBody request: RevealBidRequest
    ): ApiResponse<RevealBidResponse> {
        val bid = vickreyAuctionService.revealBid(
            auctionId = id,
            bidderId = bidderId,
            actualBid = requireNotNull(request.actualBid) { "Actual bid is required" },
            secretSalt = requireNotNull(request.secretSalt) { "Secret salt is required" }
        )

        return ApiResponse.success(bid.toRevealResponse())
    }
}