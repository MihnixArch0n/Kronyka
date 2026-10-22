package example.kronyka.presentation.controller

import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.presentation.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID
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
        @AuthenticationPrincipal sellerId: UUID,
        @Valid @RequestBody request: CreateVickreyAuctionRequest
    ): ApiResponse<CreateAuctionResponse> {
        val vickreyAuction = vickreyAuctionService.createVickreyAuction(
            sellerId = Uuid.parse(sellerId.toString()),
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
            vickreyAuctionService.getCommitmentCount(id)
        } else null

        return ApiResponse.success(auction.toDetailResponse(totalCommitments))
    }

    @DeleteMapping("/auctions/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    fun cancelAuction(
        @PathVariable id: Uuid,
        @AuthenticationPrincipal sellerId: UUID,
    ): ApiResponse<CancelAuctionResponse> {
        auctionService.cancelAuction(auctionId = id, userId = Uuid.parse(sellerId.toString()))
        return ApiResponse.success(CancelAuctionResponse(auctionId = id))
    }

    @PostMapping("/auctions/{id}/commit")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun commitBid(
        @PathVariable id: Uuid,
        @AuthenticationPrincipal bidderId: UUID,
        @Valid @RequestBody request: CommitBidRequest
    ): ApiResponse<CommitBidResponse> {
        val bid = vickreyAuctionService.commitBid(
            auctionId = id,
            bidderId = Uuid.parse(bidderId.toString()),
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
        @AuthenticationPrincipal bidderId: UUID,
        @Valid @RequestBody request: RevealBidRequest
    ): ApiResponse<RevealBidResponse> {
        val bid = vickreyAuctionService.revealBid(
            auctionId = id,
            bidderId = Uuid.parse(bidderId.toString()),
            actualBid = request.actualBid!!,
            secretSalt = request.secretSalt!!
        )

        return ApiResponse.success(bid.toRevealResponse())
    }
}