package example.kronyka.presentation.controller

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.presentation.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import kotlin.uuid.Uuid

@Tag(name = "Auctions", description = "Endpoints for auction creation, inspection, cancellation, and bidding")
@RequestMapping("/api")
interface AuctionApi {

    @Operation(
        summary = "List auctions",
        description = "Filters auctions by stored type and status, newest first; page is zero-based and size is 1 to 100"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "Successfully retrieved paginated list of auctions",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = AuctionPageResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Invalid query parameter values (e.g. page < 0, size out of range, or invalid enum name)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "InvalidPagination",
                                summary = "Page or size parameter out of allowed bounds",
                                value = OpenApiDocConstants.AUCTION_400_QUERY_BAD_REQUEST
                            )
                        ]
                    )
                ]
            )
        ]
    )
    @GetMapping("/auctions")
    fun getAuctions(
        @Parameter(description = "Filter by auction type", example = "VICKREY")
        @RequestParam(required = false) type: AuctionType?,
        @Parameter(description = "Filter by auction status", example = "COMMIT")
        @RequestParam(required = false) status: AuctionStatus?,
        @Parameter(description = "Zero-based page index (must be >= 0)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size between 1 and 100", example = "20")
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<AuctionPageResponse>

    @Operation(
        summary = "Create a new Vickrey auction",
        description = "Creates a sealed-bid Vickrey auction with reserve price, commit window, and reveal window",
        security = [SecurityRequirement(name = "BearerAuth")]
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "201",
                description = "Vickrey auction created successfully",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CreateAuctionResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Validation failed, invalid timeline, or negative reserve price",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "ValidationFailed",
                                summary = "Missing required fields or blank title",
                                value = OpenApiDocConstants.AUCTION_400_VALIDATION_ERROR
                            ),
                            ExampleObject(
                                name = "InvalidTimeline",
                                summary = "Commit end time is not before reveal end time, or start time is invalid",
                                value = OpenApiDocConstants.AUCTION_400_INVALID_TIME
                            ),
                            ExampleObject(
                                name = "InvalidReservePrice",
                                summary = "Reserve price is negative",
                                value = OpenApiDocConstants.AUCTION_400_INVALID_RESERVE_PRICE
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "Unauthenticated or invalid Bearer token",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Unauthenticated",
                                summary = "Missing or expired JWT Bearer token",
                                value = OpenApiDocConstants.AUCTION_401_UNAUTHENTICATED
                            )
                        ]
                    )
                ]
            )
        ]
    )
    @PostMapping("/auctions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createVickreyAuction(
        @AuthenticationPrincipal sellerId: Uuid,
        @Valid @RequestBody request: CreateVickreyAuctionRequest
    ): ApiResponse<CreateAuctionResponse>

    @Operation(
        summary = "Get auction details",
        description = "Retrieves public details of an auction by ID (concealing private bids during active phases)"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "Auction details retrieved successfully",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = AuctionDetailResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Invalid UUID format for auction identifier",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "InvalidUuid",
                                summary = "Malformed UUID string in path variable",
                                value = OpenApiDocConstants.AUCTION_400_TYPE_MISMATCH
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "Auction not found",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "AuctionNotFound",
                                summary = "No auction matches the provided ID",
                                value = OpenApiDocConstants.AUCTION_404_NOT_FOUND
                            )
                        ]
                    )
                ]
            )
        ]
    )
    @GetMapping("/auctions/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getAuction(
        @Parameter(description = "Auction UUID (v7)", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
        @PathVariable id: Uuid
    ): ApiResponse<AuctionDetailResponse>

    @Operation(
        summary = "Cancel an auction",
        description = "Cancels an auction before any commitments or bids have been placed",
        security = [SecurityRequirement(name = "BearerAuth")]
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "Auction cancelled successfully",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CancelAuctionResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Auction is in a terminal state (already cancelled or settled)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "InvalidAuctionState",
                                summary = "Auction has already been settled or cancelled",
                                value = OpenApiDocConstants.AUCTION_400_INVALID_STATE_CANCEL
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "Unauthenticated or invalid Bearer token",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Unauthenticated",
                                summary = "Missing or expired JWT Bearer token",
                                value = OpenApiDocConstants.AUCTION_401_UNAUTHENTICATED
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "403",
                description = "Action forbidden (only the seller who created the auction can cancel it)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "UnauthorizedAction",
                                summary = "Current user is not the creator seller of this auction",
                                value = OpenApiDocConstants.AUCTION_403_UNAUTHORIZED_ACTION
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "Auction not found",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "AuctionNotFound",
                                summary = "No auction matches the provided ID",
                                value = OpenApiDocConstants.AUCTION_404_NOT_FOUND
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "409",
                description = "Cannot cancel auction once commitments have been submitted",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "CancellationFailed",
                                summary = "Auction already has committed bids",
                                value = OpenApiDocConstants.AUCTION_409_CANCELLATION_FAILED
                            )
                        ]
                    )
                ]
            )
        ]
    )
    @DeleteMapping("/auctions/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun cancelAuction(
        @Parameter(description = "Auction UUID (v7)", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
        @PathVariable id: Uuid,
        @AuthenticationPrincipal sellerId: Uuid,
    ): ApiResponse<CancelAuctionResponse>

    @Operation(
        summary = "Commit a blind bid (Vickrey Phase 1)",
        description = "Submits a SHA-256 blind hash commitment and lockup deposit during the commit window",
        security = [SecurityRequirement(name = "BearerAuth")]
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "201",
                description = "Blind commitment recorded successfully",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CommitBidResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Invalid deposit, invalid phase, incorrect hash format, or wrong auction type",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "InvalidDeposit",
                                summary = "Lockup deposit is strictly less than auction reserve price",
                                value = OpenApiDocConstants.COMMIT_400_INVALID_DEPOSIT
                            ),
                            ExampleObject(
                                name = "InvalidAuctionState",
                                summary = "Commitment submitted outside the active COMMIT window",
                                value = OpenApiDocConstants.COMMIT_400_INVALID_STATE
                            ),
                            ExampleObject(
                                name = "InvalidHashFormat",
                                summary = "Blind hash is not a 64-character hexadecimal SHA-256 string",
                                value = OpenApiDocConstants.COMMIT_400_HASH_FORMAT
                            ),
                            ExampleObject(
                                name = "InvalidAuctionType",
                                summary = "Auction is not a sealed-bid Vickrey auction",
                                value = OpenApiDocConstants.COMMIT_400_INVALID_TYPE
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "Unauthenticated or invalid Bearer token",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Unauthenticated",
                                summary = "Missing or expired JWT Bearer token",
                                value = OpenApiDocConstants.AUCTION_401_UNAUTHENTICATED
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "Auction not found",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "AuctionNotFound",
                                summary = "No auction matches the provided ID",
                                value = OpenApiDocConstants.AUCTION_404_NOT_FOUND
                            )
                        ]
                    )
                ]
            )
        ]
    )
    @PostMapping("/auctions/{id}/commit")
    @ResponseStatus(HttpStatus.CREATED)
    fun commitBid(
        @Parameter(description = "Vickrey Auction UUID", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
        @PathVariable id: Uuid,
        @AuthenticationPrincipal bidderId: Uuid,
        @Valid @RequestBody request: CommitBidRequest
    ): ApiResponse<CommitBidResponse>

    @Operation(
        summary = "Reveal a bid (Vickrey Phase 2)",
        description = "Reveals actual bid and secret salt to verify against blind hash during the reveal window",
        security = [SecurityRequirement(name = "BearerAuth")]
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "Bid verified and revealed successfully",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = RevealBidResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Cryptographic hash mismatch, invalid auction phase, or invalid bid amount",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "RevealMismatch",
                                summary = "SHA-256(actual_bid + '_' + secret_salt) does not match committed hash (can retry)",
                                value = OpenApiDocConstants.REVEAL_400_MISMATCH
                            ),
                            ExampleObject(
                                name = "InvalidAuctionState",
                                summary = "Reveal attempt made outside of the REVEAL window",
                                value = OpenApiDocConstants.REVEAL_400_INVALID_STATE
                            ),
                            ExampleObject(
                                name = "ValidationFailed",
                                summary = "Actual bid must be greater than 0, or secret salt is blank",
                                value = OpenApiDocConstants.REVEAL_400_VALIDATION_ERROR
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "Unauthenticated or invalid Bearer token",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Unauthenticated",
                                summary = "Missing or expired JWT Bearer token",
                                value = OpenApiDocConstants.AUCTION_401_UNAUTHENTICATED
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "Auction or prior blind commitment not found",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "AuctionNotFound",
                                summary = "No auction matches the provided ID",
                                value = OpenApiDocConstants.AUCTION_404_NOT_FOUND
                            ),
                            ExampleObject(
                                name = "BidNotFound",
                                summary = "Bidder never submitted a blind commitment in Phase 1",
                                value = OpenApiDocConstants.REVEAL_404_BID_NOT_FOUND
                            )
                        ]
                    )
                ]
            )
        ]
    )
    @PostMapping("/auctions/{id}/reveal")
    @ResponseStatus(HttpStatus.OK)
    fun revealBid(
        @Parameter(description = "Vickrey Auction UUID", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
        @PathVariable id: Uuid,
        @AuthenticationPrincipal bidderId: Uuid,
        @Valid @RequestBody request: RevealBidRequest
    ): ApiResponse<RevealBidResponse>
}
