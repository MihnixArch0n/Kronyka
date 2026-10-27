package example.kronyka.presentation.controller

import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.service.AuctionService
import example.kronyka.domain.service.VickreyAuctionService
import example.kronyka.presentation.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import kotlin.uuid.Uuid

@Tag(name = "Auctions", description = "Endpoints for auction creation, inspection, cancellation, and bidding")
@RestController
@RequestMapping("/api")
class AuctionController(
    private val auctionService: AuctionService,
    private val vickreyAuctionService: VickreyAuctionService
) {
    @Operation(
        summary = "Create a new Vickrey auction",
        description = "Creates a sealed-bid Vickrey auction with reserve price, commit window, and reveal window"
    )
    @PostMapping("/auctions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createVickreyAuction(
        @AuthenticationPrincipal sellerId: Uuid,
        @Valid @RequestBody request: CreateVickreyAuctionRequest
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

    @Operation(
        summary = "Get auction details",
        description = "Retrieves public details of an auction by ID (concealing private bids during active phases)"
    )
    @GetMapping("/auctions/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getAuction(@PathVariable id: Uuid): ApiResponse<AuctionDetailResponse> {
        val auction = auctionService.getAuction(id)
        val totalCommitments = if (auction is VickreyAuction) {
            vickreyAuctionService.getCommitmentCount(id)
        } else null

        return ApiResponse.success(auction.toDetailResponse(totalCommitments))
    }

    @Operation(
        summary = "Cancel an auction",
        description = "Cancels an auction before any commitments or bids have been placed"
    )
    @DeleteMapping("/auctions/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    fun cancelAuction(
        @PathVariable id: Uuid,
        @AuthenticationPrincipal sellerId: Uuid,
    ): ApiResponse<CancelAuctionResponse> {
        auctionService.cancelAuction(auctionId = id, userId = sellerId)
        return ApiResponse.success(CancelAuctionResponse(auctionId = id))
    }

    @Operation(
        summary = "Commit a blind bid (Vickrey Phase 1)",
        description = "Submits a SHA-256 blind hash commitment and lockup deposit during the commit window"
    )
    @PostMapping("/auctions/{id}/commit")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun commitBid(
        @PathVariable id: Uuid,
        @AuthenticationPrincipal bidderId: Uuid,
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

    @Operation(
        summary = "Reveal a bid (Vickrey Phase 2)",
        description = "Reveals actual bid and secret salt to verify against blind hash during the reveal window"
    )
    @PostMapping("/auctions/{id}/reveal")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    fun revealBid(
        @PathVariable id: Uuid,
        @AuthenticationPrincipal bidderId: Uuid,
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