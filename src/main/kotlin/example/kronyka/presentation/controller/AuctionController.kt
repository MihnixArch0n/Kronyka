package example.kronyka.presentation.controller

import example.kronyka.domain.service.DutchAuctionService
import example.kronyka.domain.model.VickreyAuction
import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
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
    private val vickreyAuctionService: VickreyAuctionService,
    private val dutchAuctionService: DutchAuctionService
) {
    @Operation(
        summary = "List auctions",
        description = "Filters by stored type and status, newest first; page is zero-based and size is 1 to 100"
    )
    @GetMapping("/auctions")
    fun getAuctions(
        @RequestParam(required = false) type: AuctionType?,
        @RequestParam(required = false) status: AuctionStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<AuctionPageResponse> {
        val auctions = auctionService.getAuctions(type, status, page, size)
        return ApiResponse.success(
            AuctionPageResponse(total = auctions.total, items = auctions.items.map { it.toDetailResponse() })
        )
    }

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
    @Operation(summary = "Create a new Dutch auction")
    @PostMapping("/auctions/dutch")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createDutchAuction(
        @AuthenticationPrincipal sellerId: Uuid,
        @Valid @RequestBody request: CreateDutchAuctionRequest
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

    @Operation(summary = "Get current Dutch auction price")
    @GetMapping("/auctions/{id}/price")
    @ResponseStatus(HttpStatus.OK)
    fun getDutchPrice(
        @PathVariable id: Uuid
    ): ApiResponse<DutchPriceResponse> {

        val price = dutchAuctionService.getCurrentPrice(id)

        return ApiResponse.success(
            DutchPriceResponse(
                auctionId = id,
                currentPrice = price
            )
        )
    }

    @Operation(summary = "Buy a Dutch auction at the current price")
    @PostMapping("/auctions/{id}/buy-dutch")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    fun buyDutchAuction(
        @PathVariable id: Uuid,
        @AuthenticationPrincipal buyerId: Uuid
    ): ApiResponse<DutchBuyResponse> {
        val auction = dutchAuctionService.buyNow(
            auctionId = id,
            buyerId = buyerId
        )

        return ApiResponse.success(auction.toDutchBuyResponse())
    }
}
